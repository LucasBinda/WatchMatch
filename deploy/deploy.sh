#!/usr/bin/env bash
# Atualiza o WatchMatch na VM com a versão da main (docs/DEPLOY.md).
# Uso, na pasta do projeto: deploy/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f backend/.env ]]; then
    echo "Falta backend/.env com as chaves do TMDB e do Groq (copie de backend/.env.example)." >&2
    exit 1
fi
if ! grep -qs '^DOMAIN=.\+' .env; then
    echo "Falta DOMAIN no arquivo .env da raiz (ex.: echo DOMAIN=watchmatch.duckdns.org > .env)." >&2
    exit 1
fi

echo "==> Baixando a versão nova"
git pull --ff-only

echo "==> Construindo e subindo (o build Maven leva alguns minutos)"
compose=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)
"${compose[@]}" up -d --build --wait

echo "==> Limpando imagens antigas"
docker image prune -f >/dev/null

"${compose[@]}" ps
echo "==> Pronto: https://$(grep '^DOMAIN=' .env | cut -d= -f2)"
