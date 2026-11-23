# Eval da IA

Mede se a IA do WatchMatch entende bem as frases das pessoas. Não é um teste que passa ou falha.
Os testes do `mvn test` usam uma IA falsa (Mockito) e sempre dão a mesma resposta. O eval chama a IA
de verdade (Groq) pelo gateway, como o site faz. Como ela responde um pouco diferente a cada vez,
o resultado é uma **nota**, como "24 de 28 casos certos".

Serve para comparar: mudou o prompt (por exemplo, de `intent.v2` para `intent.v3`) ou o modelo?
Rode o eval de novo e veja se a nota subiu ou caiu.

## Como rodar

Com o Docker Compose no ar (veja [COMO_RODAR](../../docs/COMO_RODAR.md)):

```bash
python3 backend/eval/run_eval.py --label intent.v2
```

Precisa do Python 3.10 ou mais novo e só usa a biblioteca padrão. Leva uns 6 minutos: espera 10 s
entre os casos para respeitar o limite do gateway (6 buscas por minuto por IP) e os limites do Groq.
Cada caso gasta duas chamadas do Groq.

Outras opções:

```bash
python3 backend/eval/run_eval.py --only terror-curto,vago   # só alguns casos
```

```bash
python3 backend/eval/run_eval.py --compare backend/eval/results/A.json backend/eval/results/B.json
```

Cada rodada é salva em `results/` com data e rótulo: o `.json` tem tudo, e o `.md` é o relatório.

## Comparar versões do prompt

A versão do prompt do entendimento vem de `LLM_INTENT_PROMPT` (padrão `intent.v3`). Para medir uma
mudança, rode cada versão pelo menos duas vezes, alternando: a IA varia de uma rodada para outra.

```bash
LLM_INTENT_PROMPT=intent.v2 docker compose up -d llm-service
```

```bash
python3 backend/eval/run_eval.py --ai-mode filters --pause 15 --label intent.v2-filters
```

`--ai-mode filters` pula a escolha da IA (rerank). Ela não entra na nota do entendimento, gasta a cota de
tokens mais apertada do Groq e deixaria a rodada lenta. Use o modo padrão (`pick`) para medir o sistema
inteiro, com `--pause 30`: o modelo do rerank aceita só umas 2 buscas por minuto no plano grátis.

## O que ele confere

Para cada caso de [cases.json](cases.json):

1. **Entendimento.** Os critérios que a IA tirou da frase batem com o esperado: gênero, duração,
   ano, idioma, plataforma, classificação, palavras-chave e "parecido com X". Também confere que a
   IA não inventou restrições que a frase não pediu (`absent`) e que ela recusa pedidos fora do tema
   ou tentativas de prompt injection.
2. **Resultado.** Cada título devolvido está numa plataforma do usuário (assinatura, grátis ou com
   anúncios) e respeita os critérios usados na busca.

| Métrica | Meta |
| ------- | ---- |
| IA de entendimento respondeu (JSON válido) | ~100% |
| Títulos disponíveis na plataforma pedida | ~100% |
| Critérios respeitados (entendimento e títulos) | alto |
| Títulos fora do catálogo | 0 |
| Latência por requisição | registrar (p50/p95) |

Quando a busca volta vazia, o sistema afrouxa os critérios da IA (veja ARQUITETURA, "afrouxamento").
Nesses casos a resposta mostra os critérios afrouxados, não o que a IA entendeu, então essas
conferências ficam de fora da nota. O relatório diz quantas foram.

## Como escrever um caso

```json
{
  "id": "terror-curto",
  "kind": "normal",
  "query": "um terror curto pra hoje à noite",
  "providers": [8, 119],
  "expect": { "genresAny": [27], "runtimeMax": [80, 110] }
}
```

Coloque em `expect` só o que a frase deixa claro. Faixas `[min, max]` aceitam variações razoáveis
("curto" pode virar 90 ou 100). As chaves aceitas estão explicadas no topo de `cases.json`.
Os casos `dificil` cobrem gíria, frase vaga, pedido impossível e prompt injection. Os casos
`generalizacao` usam sinais implícitos que o prompt não cita com essas palavras ("dorama", "Bollywood",
"sobrinha de 11 anos"). Não copie frases do eval para os exemplos do prompt: a nota subiria sem a IA
ter melhorado de verdade.
