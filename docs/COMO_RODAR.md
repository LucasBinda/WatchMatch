# Como rodar o WatchMatch

Todos os comandos partem da **raiz do projeto**.

## 1. Chaves das APIs (uma vez só)

| API | Onde criar | O que copiar |
| --- | ---------- | ------------ |
| TMDB (catálogo) | <https://www.themoviedb.org/settings/api> (pede uma conta gratuita) | **API Read Access Token**, o token longo que começa com `eyJ` |
| Groq (IA) | <https://console.groq.com/keys> | a chave que começa com `gsk_` |

```bash
cp backend/.env.example backend/.env
```

Preencha `TMDB_API_KEY` e `GROQ_API_KEY` em `backend/.env`. Esse arquivo não vai para o Git.
Sem a chave do Groq o site funciona sem IA: os títulos vêm na ordem do catálogo, sem justificativa.

## 2. Tudo de uma vez, com Docker

```bash
docker compose up --build
```

Abra <http://localhost:8080>. Cada serviço roda no seu próprio container, e só o gateway fica exposto.
O gateway limita as buscas: 6 por minuto por IP (com folga para 4 seguidas) e 20 por minuto no site todo.
Passou disso, o site mostra "Muitas buscas seguidas" e libera de novo em alguns segundos.
O primeiro build baixa as dependências do Maven e do npm e demora alguns minutos; os próximos são rápidos.

| Ação | Comando |
| ---- | ------- |
| Subir em segundo plano | `docker compose up --build -d` |
| Subir e esperar tudo ficar pronto | `docker compose up --build -d --wait` |
| Ver se cada serviço está saudável | `docker compose ps` (coluna STATUS mostra `healthy`) |
| Ver os logs (todos / um) | `docker compose logs -f` · `docker compose logs -f llm-service` |
| Ver quem o rate limit barrou | `docker compose logs gateway \| grep limiting` |
| Reconstruir um serviço depois de mudar o código | `docker compose up --build -d catalog-service` |
| Parar tudo | `docker compose down` |

### Instalar o Docker (Arch Linux)

```bash
sudo pacman -S docker docker-compose docker-buildx
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # depois saia e entre de novo na sessão
```

Se aparecer `permission denied while trying to connect to the docker API`, a sessão ainda não carregou o grupo
`docker`: saia e entre de novo (ou reinicie). Para usar na hora, abra um shell com o grupo: `newgrp docker`.

## 3. Cada parte separada, sem Docker

Precisa de Java 21+ e Node 22+. Um terminal para cada.

| Parte | Comando | Porta |
| ----- | ------- | ----- |
| catalog-service | `cd backend/catalog-service && ./mvnw spring-boot:run` | 8081 |
| recommendation-service | `cd backend/recommendation-service && ./mvnw spring-boot:run` | 8082 |
| llm-service | `cd backend/llm-service && ./mvnw spring-boot:run` | 8083 |
| frontend com o backend real | `cd frontend && npm install && npm run dev:backend` | 5173 |
| frontend só com dados de exemplo (mock) | `cd frontend && npm install && npm run dev` | 5173 |

Abra <http://localhost:5173>. No modo mock aparece uma faixa vermelha **MOCK MODE** no topo.
Se a porta 5173 estiver ocupada (outro `npm run dev` aberto), o Vite usa a 5174: pare o antigo com Ctrl+C.

## 4. Testes e verificações

| O quê | Comando |
| ----- | ------- |
| Testes de um serviço | `cd backend/catalog-service && ./mvnw test` (o mesmo nas outras pastas) |
| Build e tipos do frontend | `cd frontend && npm run build` |
| Lint do frontend | `cd frontend && npm run lint` |
| Lint dos contratos OpenAPI | `cd backend/contracts && npx @redocly/cli lint` |
| Eval da IA (com o Docker no ar) | `python3 backend/eval/run_eval.py --label teste` (ver [backend/eval/](../backend/eval/README.md)) |

## 5. Publicar na internet

O site de produção roda neste computador, com o mesmo Compose, e um túnel grátis da Cloudflare o publica com HTTPS,
sem abrir porta no roteador. Detalhes, motivos e limites em [DEPLOY.md](DEPLOY.md).

| Ação | Comando |
| ---- | ------- |
| Subir (ou atualizar) a produção | `deploy/deploy.sh` |
| Derrubar a produção, inclusive o túnel | `deploy/deploy.sh parar` |
| Ver o endereço do túnel de novo | `docker compose -f docker-compose.yml -f docker-compose.prod.yml logs tunnel \| grep trycloudflare` |

Derrube o Compose de casa antes de subir a produção (`docker compose down`): as duas usam faixas de rede diferentes.
O `deploy.sh` faz o build, sobe tudo e, no fim, mostra o endereço público do túnel, algo como
`https://palavras-aleatorias.trycloudflare.com`.

### Link fixo pelo GitHub Pages

O endereço do túnel muda toda vez que ele reinicia. Para ter um link que não muda, usamos o **GitHub Pages** do
repositório como redirecionador:

**<https://lucasbinda.github.io/WatchMatch/>**

- É uma página só ([deploy/link.html](../deploy/link.html)) publicada na branch `gh-pages`. Ela confere se o site
  responde e redireciona para o endereço atual do túnel; com o site desligado, mostra que está fora do ar.
- O `deploy.sh` atualiza essa página sozinho, e só quando o endereço do túnel ou a `link.html` mudam (uma
  publicação por apresentação, não a cada comando). Derrubar não publica nada.
- Na primeira vez, ative o GitHub Pages para a branch `gh-pages` (Settings → Pages, ou o comando em
  [DEPLOY.md](DEPLOY.md#link-fixo-uma-vez-só)).
- Depois de uma publicação, o link leva de 1 a 10 minutos para apontar para o endereço novo (build do Pages e cache).
- O site continua dependendo deste computador **ligado, conectado e sem suspender**.

