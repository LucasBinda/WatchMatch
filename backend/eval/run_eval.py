#!/usr/bin/env python3
"""Eval do componente de IA do WatchMatch (docs/ARQUITETURA.md, seção 8).

Manda cada frase de cases.json para o gateway, como o site faz, e confere duas coisas:
  1. entendimento: os critérios que a IA tirou da frase batem com o esperado do caso;
  2. resultado: cada título devolvido está numa plataforma do usuário e respeita os critérios usados.

Não é um teste que passa ou falha: a IA responde diferente a cada vez, então o eval dá uma nota.
Rode de novo depois de mexer no prompt e compare as notas (--compare).

Uso (com o Docker Compose no ar):
    python3 backend/eval/run_eval.py --label intent.v2
    python3 backend/eval/run_eval.py --only terror-curto,vago
    python3 backend/eval/run_eval.py --ai-mode filters --label intent.v3   # só o entendimento
    python3 backend/eval/run_eval.py --compare backend/eval/results/A.json backend/eval/results/B.json

Só usa a biblioteca padrão do Python (3.10+).
"""

import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
INCLUDED_OFFERS = {"flatrate", "free", "ads"}
CERTIFICATIONS = ["L", "10", "12", "14", "16", "18"]
# Textos que o recommendation-service põe em notices (RecommendService.java)
OFF_TOPIC = "Não entendi isso como um pedido de filme ou série"
RELAXED_ALL = "mostrando resultados só com os seus filtros"
RELAXED_PARTIAL = "deixei de lado: "
INTENT_DOWN = ("A IA não respondeu desta vez", "A IA está fora do ar por alguns instantes: a busca")
PICK_DOWN = ("A IA não conseguiu escolher", "A IA está fora do ar por alguns instantes: os títulos",
             "Muita gente buscando agora")
# Nome que aparece no aviso de afrouxamento → campo dos critérios
DROPPED_FIELDS = {"palavras-chave": "keywords", "idioma": "originalLanguage", "duração mínima": "runtimeMin"}
AI_FIELDS = ["mediaType", "genres", "excludeGenres", "runtimeMin", "runtimeMax", "yearFrom", "yearTo",
             "originalLanguage", "certificationMax", "keywords", "providers", "referenceTitle"]


# ---------- HTTP ----------

def call(base, method, path, body=None, timeout=90):
    """Devolve (status, json, segundos). Em 429 espera o Retry-After do gateway e tenta de novo."""
    data = json.dumps(body).encode() if body is not None else None
    for _ in range(5):
        req = urllib.request.Request(base + path, data=data, method=method,
                                     headers={"Content-Type": "application/json", "Accept": "application/json"})
        start = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.status, json.loads(resp.read() or b"null"), time.monotonic() - start
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait = int(e.headers.get("Retry-After", "10"))
                print(f"    (429 do gateway, esperando {wait} s)", flush=True)
                time.sleep(wait)
                continue
            raw = e.read()
            try:
                payload = json.loads(raw)
            except ValueError:
                payload = raw.decode(errors="replace")
            return e.code, payload, time.monotonic() - start
        except (urllib.error.URLError, TimeoutError) as e:
            return None, str(e), time.monotonic() - start
    return 429, "rate limit do gateway não liberou", 0.0


# ---------- entendimento ----------

def is_set(value):
    if isinstance(value, (list, str)):
        return len(value) > 0
    return value is not None


def in_range(value, bounds):
    return value is not None and bounds[0] <= value <= bounds[1]


def cert_rank(value):
    return CERTIFICATIONS.index(value) if value in CERTIFICATIONS else None


def dropped_fields(notices):
    """Campos que o afrouxamento tirou: a resposta mostra os critérios da última tentativa, não o que a IA
    entendeu. Esses campos não podem ser conferidos."""
    for n in notices:
        if RELAXED_ALL in n:
            return set(AI_FIELDS)
        if RELAXED_PARTIAL in n:
            names = n.split(RELAXED_PARTIAL, 1)[1].rstrip(".").split(", ")
            return {DROPPED_FIELDS[name] for name in names if name in DROPPED_FIELDS}
    return set()


def understanding_checks(expect, crit, notices, raw_text):
    """Lista de (nome, ok, detalhe); ok = None quando não dá para conferir."""
    checks = []
    dropped = dropped_fields(notices)
    off_topic = any(OFF_TOPIC in n for n in notices)

    def add(name, field, ok, detail):
        if field in dropped:
            checks.append((name, None, "afrouxado pelo sistema, não dá para conferir"))
        else:
            checks.append((name, ok, detail))

    if "offTopic" in expect:
        checks.append(("offTopic", off_topic == expect["offTopic"], f"fora do tema={off_topic}"))
    if "mediaType" in expect:
        v = crit.get("mediaType")
        add("mediaType", "mediaType", v == expect["mediaType"], f"entendeu {v!r}")
    if "genresAny" in expect:
        v = crit.get("genres") or []
        add("genresAny", "genres", any(g in v for g in expect["genresAny"]), f"genres={v}")
    if "genresNone" in expect:
        v = crit.get("genres") or []
        add("genresNone", "genres", not any(g in v for g in expect["genresNone"]), f"genres={v}")
    if "excludeGenresAll" in expect:
        v = crit.get("excludeGenres") or []
        add("excludeGenresAll", "excludeGenres", all(g in v for g in expect["excludeGenresAll"]),
            f"excludeGenres={v}")
    for field in ("runtimeMin", "runtimeMax", "yearFrom", "yearTo"):
        if field in expect:
            v = crit.get(field)
            add(field, field, in_range(v, expect[field]), f"entendeu {v}, aceito {expect[field]}")
    if "originalLanguage" in expect:
        v = crit.get("originalLanguage")
        add("originalLanguage", "originalLanguage", v == expect["originalLanguage"], f"entendeu {v!r}")
    if "certificationMaxAtMost" in expect:
        v = crit.get("certificationMax")
        rank, limit = cert_rank(v), cert_rank(expect["certificationMaxAtMost"])
        add("certificationMaxAtMost", "certificationMax", rank is not None and rank <= limit,
            f"entendeu {v!r}, aceito até {expect['certificationMaxAtMost']!r}")
    if "keywordsAny" in expect:
        v = [k.lower() for k in crit.get("keywords") or []]
        add("keywordsAny", "keywords", any(k in v for k in expect["keywordsAny"]), f"keywords={v}")
    if "providers" in expect:
        v = crit.get("providers") or []
        add("providers", "providers", sorted(v) == sorted(expect["providers"]), f"providers={v}")
    if "referenceTitle" in expect:
        v = crit.get("referenceTitle") or ""
        add("referenceTitle", "referenceTitle", any(t in v.lower() for t in expect["referenceTitle"]),
            f"referência={v!r}")
    for field, wanted in (expect.get("sources") or {}).items():
        v = (crit.get("sources") or {}).get(field)
        checks.append((f"sources.{field}", v == wanted, f"veio de {v!r}"))
    for field in expect.get("absent", []):
        v = crit.get(field)
        # Fora do tema, a IA não aplica nada; aqui só interessa se ela inventou restrição
        source = (crit.get("sources") or {}).get(field)
        invented = is_set(v) and source == "ai"
        checks.append((f"sem {field}", not invented, f"{field}={v!r}" if invented else "não inventou"))
    for text in expect.get("responseNotContains", []):
        checks.append((f"não vaza {text!r}", text not in raw_text, "vazou" if text in raw_text else "ok"))
    if "noticeAny" in expect:
        ok = any(t in n for t in expect["noticeAny"] for n in notices)
        checks.append(("aviso ao usuário", ok, f"notices={notices}"))
    return checks


# ---------- resultado ----------

def item_problems(item, crit, language_of):
    """Motivos pelos quais o título não respeita os critérios usados. Lista vazia = respeita."""
    problems = []
    if crit.get("mediaType") and item.get("mediaType") != crit["mediaType"]:
        problems.append(f"tipo {item.get('mediaType')}")
    runtime = item.get("runtime")
    if runtime is not None:
        if crit.get("runtimeMin") is not None and runtime < crit["runtimeMin"]:
            problems.append(f"{runtime} min < mínimo {crit['runtimeMin']}")
        if crit.get("runtimeMax") is not None and runtime > crit["runtimeMax"]:
            problems.append(f"{runtime} min > máximo {crit['runtimeMax']}")
    year = item.get("year")
    if year is not None:
        if crit.get("yearFrom") is not None and year < crit["yearFrom"]:
            problems.append(f"ano {year} < {crit['yearFrom']}")
        if crit.get("yearTo") is not None and year > crit["yearTo"]:
            problems.append(f"ano {year} > {crit['yearTo']}")
    genres = [g["id"] for g in item.get("genres") or []]
    if crit.get("genres") and genres and not any(g in genres for g in crit["genres"]):
        problems.append(f"gêneros {genres} sem nenhum de {crit['genres']}")
    banned = [g for g in genres if g in (crit.get("excludeGenres") or [])]
    if banned:
        problems.append(f"gênero proibido {banned}")
    rank, limit = cert_rank(item.get("certification")), cert_rank(crit.get("certificationMax"))
    if rank is not None and limit is not None and rank > limit:
        problems.append(f"classificação {item['certification']} > {crit['certificationMax']}")
    if crit.get("originalLanguage"):
        lang = language_of(item)
        if lang is not None and lang != crit["originalLanguage"]:
            problems.append(f"idioma {lang}")
    return problems


def available(item, providers):
    offers = (item.get("watch") or {}).get("providers") or []
    return any(p.get("id") in providers and p.get("offer") in INCLUDED_OFFERS for p in offers)


# ---------- um caso ----------

def run_case(base, case, ai_mode):
    body = {"query": case["query"], "providers": case["providers"], "aiMode": ai_mode}
    if case.get("filters"):
        body["filters"] = case["filters"]
    status, resp, seconds = call(base, "POST", "/api/recommend", body)
    result = {"id": case["id"], "kind": case.get("kind", "normal"), "query": case["query"],
              "status": status, "seconds": round(seconds, 2)}
    if status != 200 or not isinstance(resp, dict):
        result["error"] = resp if isinstance(resp, str) else json.dumps(resp, ensure_ascii=False)[:300]
        return result

    crit = resp.get("criteria") or {}
    notices = resp.get("notices") or []
    items = resp.get("items") or []
    raw_text = json.dumps(resp, ensure_ascii=False)

    languages = {}

    def language_of(item):
        key = (item["mediaType"], item["id"])
        if key not in languages:
            st, detail, _ = call(base, "GET", f"/api/titles/{item['mediaType']}/{item['id']}", timeout=30)
            languages[key] = detail.get("originalLanguage") if st == 200 and isinstance(detail, dict) else None
        return languages[key]

    checks = understanding_checks(case.get("expect", {}), crit, notices, raw_text)
    item_rows = []
    for it in items:
        item_rows.append({
            "id": it.get("id"), "mediaType": it.get("mediaType"), "title": it.get("title"),
            "available": available(it, crit.get("providers") or case["providers"]),
            "problems": item_problems(it, crit, language_of),
            "hasReason": bool(it.get("reason")),
        })
    ids = [(r["mediaType"], r["id"]) for r in item_rows]

    result.update({
        "criteria": crit,
        "notices": notices,
        "degraded": resp.get("degraded"),
        "intentOk": not any(n.startswith(INTENT_DOWN) for n in notices),
        "pickOk": not any(n.startswith(PICK_DOWN) for n in notices),
        "checks": [{"name": n, "ok": ok, "detail": d} for n, ok, d in checks],
        "items": item_rows,
        "duplicates": len(ids) - len(set(ids)),
        # Grounding: o recommendation-service só devolve títulos que vieram do catálogo; aqui conferimos
        # que todos têm id e a ficha de onde assistir, que só existe para títulos reais do TMDB
        "ungrounded": sum(1 for it in items if not it.get("id") or it.get("watch") is None),
    })
    return result


# ---------- relatório ----------

def pct(part, whole):
    return f"{100 * part / whole:.0f}%" if whole else "—"


def percentile(values, p):
    if not values:
        return None
    ordered = sorted(values)
    k = max(0, min(len(ordered) - 1, round(p / 100 * len(ordered) + 0.5) - 1))
    return ordered[k]


def summarize(results):
    ok_cases = [r for r in results if r.get("status") == 200]
    checks = [c for r in ok_cases for c in r["checks"] if c["ok"] is not None]
    skipped = sum(1 for r in ok_cases for c in r["checks"] if c["ok"] is None)
    items = [it for r in ok_cases for it in r["items"]]
    case_pass = [r for r in ok_cases if all(c["ok"] is not False for c in r["checks"])]
    latencies = [r["seconds"] for r in results if r.get("status") == 200]
    return {
        "cases": len(results),
        "http200": len(ok_cases),
        "intentOk": sum(1 for r in ok_cases if r["intentOk"]),
        "pickOk": sum(1 for r in ok_cases if r["pickOk"]),
        "casesPassed": len(case_pass),
        "checksPassed": sum(1 for c in checks if c["ok"]),
        "checksTotal": len(checks),
        "checksSkipped": skipped,
        "items": len(items),
        "itemsAvailable": sum(1 for it in items if it["available"]),
        "itemsRespecting": sum(1 for it in items if not it["problems"]),
        "ungrounded": sum(r["ungrounded"] for r in ok_cases),
        "duplicates": sum(r["duplicates"] for r in ok_cases),
        "emptyResults": sum(1 for r in ok_cases if not r["items"]),
        "p50": percentile(latencies, 50),
        "p95": percentile(latencies, 95),
    }


def metrics_table(s):
    return [
        ("Respostas 200 do gateway", f"{s['http200']}/{s['cases']}", "100%"),
        ("IA de entendimento respondeu (JSON válido)", f"{s['intentOk']}/{s['http200']} ({pct(s['intentOk'], s['http200'])})", "~100%"),
        ("IA de escolha respondeu", f"{s['pickOk']}/{s['http200']} ({pct(s['pickOk'], s['http200'])})", "alto"),
        ("Casos com entendimento certo", f"{s['casesPassed']}/{s['http200']} ({pct(s['casesPassed'], s['http200'])})", "alto"),
        ("Conferências de entendimento certas", f"{s['checksPassed']}/{s['checksTotal']} ({pct(s['checksPassed'], s['checksTotal'])})", "alto"),
        ("Títulos disponíveis na plataforma", f"{s['itemsAvailable']}/{s['items']} ({pct(s['itemsAvailable'], s['items'])})", "~100%"),
        ("Títulos que respeitam os critérios", f"{s['itemsRespecting']}/{s['items']} ({pct(s['itemsRespecting'], s['items'])})", "alto"),
        ("Títulos fora do catálogo", str(s["ungrounded"]), "0"),
        ("Títulos repetidos na página", str(s["duplicates"]), "0"),
        ("Latência p50 / p95", f"{s['p50']} s / {s['p95']} s", "registrar"),
    ]


def report(results, summary, meta):
    lines = [f"# Eval da IA: {meta['label']}", "",
             f"Rodado em {meta['date']} contra `{meta['base']}`, {summary['cases']} casos, "
             f"modo `{meta.get('aiMode', 'pick')}`.", "",
             "| Métrica | Resultado | Meta |", "| --- | --- | --- |"]
    lines += [f"| {a} | {b} | {c} |" for a, b, c in metrics_table(summary)]
    if summary["checksSkipped"]:
        lines += ["", f"{summary['checksSkipped']} conferências ficaram de fora porque o sistema afrouxou a "
                      "busca (a resposta mostra os critérios afrouxados, não o que a IA entendeu)."]
    if summary["emptyResults"]:
        lines += ["", f"{summary['emptyResults']} casos voltaram sem nenhum título."]
    lines += ["", "## Casos", "", "| Caso | Tipo | Entendimento | Títulos ok | Tempo | Avisos |",
              "| --- | --- | --- | --- | --- | --- |"]
    for r in results:
        if r.get("status") != 200:
            lines.append(f"| {r['id']} | {r['kind']} | erro HTTP {r.get('status')} | — | {r['seconds']} s | |")
            continue
        conf = [c for c in r["checks"] if c["ok"] is not None]
        good = sum(1 for c in conf if c["ok"])
        mark = "✅" if good == len(conf) else "❌"
        items_ok = sum(1 for it in r["items"] if it["available"] and not it["problems"])
        notices = "; ".join(r["notices"]).replace("|", "/")
        lines.append(f"| {r['id']} | {r['kind']} | {mark} {good}/{len(conf)} | {items_ok}/{len(r['items'])} "
                     f"| {r['seconds']} s | {notices} |")
    failures = [(r, c) for r in results if r.get("status") == 200 for c in r["checks"] if c["ok"] is False]
    bad_items = [(r, it) for r in results if r.get("status") == 200 for it in r["items"]
                 if not it["available"] or it["problems"]]
    if failures or bad_items:
        lines += ["", "## O que errou", ""]
        for r, c in failures:
            lines.append(f"- **{r['id']}** ({r['query']}): {c['name']}, {c['detail']}")
        for r, it in bad_items:
            why = it["problems"] + ([] if it["available"] else ["fora das plataformas do usuário"])
            lines.append(f"- **{r['id']}**: \"{it['title']}\" ({', '.join(why)})")
    return "\n".join(lines) + "\n"


def compare(path_a, path_b):
    a, b = (json.loads(Path(p).read_text()) for p in (path_a, path_b))
    rows_a, rows_b = metrics_table(a["summary"]), metrics_table(b["summary"])
    print(f"| Métrica | {a['meta']['label']} | {b['meta']['label']} |")
    print("| --- | --- | --- |")
    for (name, va, _), (_, vb, _) in zip(rows_a, rows_b):
        print(f"| {name} | {va} | {vb} |")
    by_id = {r["id"]: r for r in a["results"]}
    changed = []
    for r in b["results"]:
        old = by_id.get(r["id"])
        if not old or old.get("status") != 200 or r.get("status") != 200:
            continue
        passed = lambda x: all(c["ok"] is not False for c in x["checks"])
        if passed(old) != passed(r):
            changed.append(f"- {r['id']}: {'passou a acertar' if passed(r) else 'passou a errar'}")
    if changed:
        print("\nCasos que mudaram:")
        print("\n".join(changed))


def main():
    parser = argparse.ArgumentParser(description="Eval do componente de IA do WatchMatch")
    parser.add_argument("--base", default="http://localhost:8080", help="endereço do gateway")
    parser.add_argument("--cases", default=str(HERE / "cases.json"))
    parser.add_argument("--label", default="sem-rotulo", help="nome da rodada, ex.: intent.v2")
    parser.add_argument("--only", help="ids separados por vírgula")
    parser.add_argument("--pause", type=float, default=10.0,
                        help="segundos entre casos (o gateway deixa 6 buscas por minuto por IP)")
    parser.add_argument("--ai-mode", choices=["pick", "filters"], default="pick",
                        help="filters pula a escolha da IA (rerank): mede só o entendimento, gasta metade do Groq")
    parser.add_argument("--compare", nargs=2, metavar=("A.json", "B.json"), help="compara duas rodadas salvas")
    args = parser.parse_args()

    if args.compare:
        compare(*args.compare)
        return 0

    cases = json.loads(Path(args.cases).read_text())["cases"]
    if args.only:
        wanted = set(args.only.split(","))
        cases = [c for c in cases if c["id"] in wanted]
        if not cases:
            print("Nenhum caso com esses ids.", file=sys.stderr)
            return 2

    status, _, _ = call(args.base, "GET", "/health", timeout=5)
    if status != 200:
        print(f"O gateway não respondeu em {args.base}/health. O Docker Compose está no ar?", file=sys.stderr)
        return 2

    results = []
    for i, case in enumerate(cases):
        if i:
            time.sleep(args.pause)
        print(f"[{i + 1}/{len(cases)}] {case['id']}: {case['query']}", flush=True)
        r = run_case(args.base, case, args.ai_mode)
        if r.get("status") == 200:
            conf = [c for c in r["checks"] if c["ok"] is not None]
            good = sum(1 for c in conf if c["ok"])
            print(f"    entendimento {good}/{len(conf)}, {len(r['items'])} títulos, {r['seconds']} s", flush=True)
        else:
            print(f"    erro: HTTP {r.get('status')} {r.get('error', '')[:120]}", flush=True)
        results.append(r)

    summary = summarize(results)
    now = datetime.now()
    meta = {"label": args.label, "date": now.strftime("%Y-%m-%d %H:%M"), "base": args.base, "aiMode": args.ai_mode}
    text = report(results, summary, meta)

    out = HERE / "results"
    out.mkdir(exist_ok=True)
    stem = f"{now:%Y%m%d-%H%M}-{args.label}"
    (out / f"{stem}.json").write_text(json.dumps({"meta": meta, "summary": summary, "results": results},
                                                 ensure_ascii=False, indent=2) + "\n")
    (out / f"{stem}.md").write_text(text)
    print()
    print(text)
    print(f"Salvo em {out / stem}.json e .md")
    return 0


if __name__ == "__main__":
    sys.exit(main())
