# Backend — WatchMatch

Backend em **microsserviços**, orquestrados com Docker Compose. Arquitetura completa em [docs/ARQUITETURA.md](../docs/ARQUITETURA.md).

## Serviços

| Serviço | Responsabilidade |
| ------- | ---------------- |
| `gateway/` | Porta única do site (Nginx): roteamento, rate limit por IP, limite do corpo, `X-Request-Id` |
| `recommendation-service/` | Orquestra o fluxo de recomendação |
| `llm-service/` | Integração com o **Groq** (intenção → critérios; rerank + justificativas) |
| `catalog-service/` | Integração com o **TMDB** (catálogo e "onde assistir") |
| `feedback-service/` | Feedback do usuário e métricas (C3) |

Linguagem padrão: **Java 21 + Spring Boot 4**. Outras linguagens são permitidas desde que o serviço cumpra seu contrato em `contracts/` e as convenções da seção 3 da arquitetura.

## Como rodar

Tudo com Docker (`docker compose up --build` na raiz) ou cada serviço separado: ver
[docs/COMO_RODAR.md](../docs/COMO_RODAR.md). Cada serviço tem seu `Dockerfile`.

Sem `GROQ_API_KEY` o site continua funcionando, só que sem a IA: a resposta sai com `degraded: true`
e os títulos vêm na ordem do catálogo.

## Ainda falta

- no catalog: `keywords`, `originalLanguage` e `runtimeMin` ainda não viram filtros no TMDB, e `/search` e `/similar`
  (para "parecido com X") não existem.
