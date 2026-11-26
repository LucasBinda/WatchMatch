#!/usr/bin/env bash
# Sobe o WatchMatch de produção com o túnel da Cloudflare e mostra o endereço público (docs/DEPLOY.md).
# Uso, na pasta do projeto:
#   deploy/deploy.sh          sobe (ou atualiza) e, se o endereço do túnel mudou, aponta o link fixo para ele
#   deploy/deploy.sh parar    derruba tudo, inclusive o túnel (o link fixo percebe sozinho que caiu)
# Sobe o código que está na pasta agora. Para publicar a versão da main, rode git pull antes.
set -euo pipefail

cd "$(dirname "$0")/.."

compose=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)

# Link fixo: publica deploy/link.html na branch gh-pages com o endereço do túnel. O GitHub Pages serve essa
# página em https://<usuário>.github.io/<repositório>/ e ela redireciona para o endereço do dia.
# Cada publicação é um commit na gh-pages e um deploy do GitHub Pages, então só publica quando precisa: quando
# o endereço do túnel mudou ou quando a link.html mudou. Subir de novo com o túnel de pé não publica nada, e
# derrubar também não (a página confere sozinha se o site responde e mostra "fora do ar").
# O commit é montado direto no Git (hash-object, mktree, commit-tree): não troca de branch nem mexe na
# pasta de trabalho ou na main. Para não publicar: PUBLICAR_LINK=0 deploy/deploy.sh
publish_link() {
    local url=$1
    if [[ "${PUBLICAR_LINK:-1}" == "0" ]]; then
        return 0
    fi
    # Impressão digital do que vai ao ar: o endereço + o modelo da página (sem a data)
    local fingerprint published
    fingerprint=$( { echo "$url"; cat deploy/link.html; } | git hash-object --stdin)
    git fetch -q origin gh-pages 2>/dev/null || true
    published=$(git show refs/remotes/origin/gh-pages:index.html 2>/dev/null \
        | grep -o 'link-fixo: [0-9a-f]*' | cut -d' ' -f2 || true)
    if [[ "$published" == "$fingerprint" ]]; then
        echo "==> Link fixo já aponta para este endereço (nada a publicar)"
        return 0
    fi
    local page blob nojekyll tree parent commit
    page=$(sed -e "s#__URL__#${url}#g" -e "s#__QUANDO__#$(date '+%d/%m/%Y %H:%M')#g" deploy/link.html)
    page="${page}"$'\n'"<!-- link-fixo: ${fingerprint} -->"
    blob=$(printf '%s\n' "$page" | git hash-object -w --stdin)
    nojekyll=$(git hash-object -w --stdin </dev/null)
    tree=$(printf '100644 blob %s\tindex.html\n100644 blob %s\t.nojekyll\n' "$blob" "$nojekyll" | git mktree)
    parent=$(git rev-parse -q --verify refs/remotes/origin/gh-pages || true)
    commit=$(git commit-tree "$tree" ${parent:+-p "$parent"} -m "link fixo: ${url:-fora do ar}")
    if git push -q origin "$commit:refs/heads/gh-pages"; then
        echo "==> Link fixo atualizado (o GitHub Pages leva de 1 a 10 minutos para mostrar a mudança)"
    else
        echo "Não consegui atualizar o link fixo (git push na gh-pages falhou). O site está no ar mesmo assim." >&2
    fi
}

# Endereço público do GitHub Pages deste repositório (ex.: https://lucasbinda.github.io/WatchMatch/)
pages_url() {
    local remote owner repo
    remote=$(git remote get-url origin 2>/dev/null || true)
    if [[ "$remote" =~ github\.com[:/]([^/]+)/([^/.]+) ]]; then
        owner=$(echo "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')
        repo=${BASH_REMATCH[2]}
        echo "https://${owner}.github.io/${repo}/"
    fi
}

if [[ "${1:-}" == "parar" ]]; then
    echo "==> Derrubando a produção (inclusive o túnel)"
    "${compose[@]}" down
    # Nada a publicar: o link fixo tenta abrir o site, não consegue e mostra "fora do ar"
    exit 0
fi

if [[ ! -f backend/.env ]]; then
    echo "Falta backend/.env com as chaves do TMDB e do Groq (copie de backend/.env.example)." >&2
    exit 1
fi

echo "==> Construindo e subindo (o build Maven leva alguns minutos)"
"${compose[@]}" up -d --build --wait

echo "==> Limpando imagens antigas"
docker image prune -f >/dev/null

"${compose[@]}" ps

echo "==> Esperando o endereço do túnel"
for _ in $(seq 30); do
    url=$("${compose[@]}" logs tunnel 2>/dev/null | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | tail -n 1 || true)
    if [[ -n "$url" ]]; then
        echo "==> Pronto: $url"
        publish_link "$url"
        link=$(pages_url)
        if [[ -n "$link" && "${PUBLICAR_LINK:-1}" != "0" ]]; then
            echo "==> Link fixo para entregar: $link"
        fi
        exit 0
    fi
    sleep 2
done
echo "O túnel não mostrou o endereço em 1 minuto. Veja: ${compose[*]} logs tunnel" >&2
exit 1
