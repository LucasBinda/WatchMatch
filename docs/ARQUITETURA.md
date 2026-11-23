# Arquitetura e plano técnico — WatchMatch

Documento vivo; decisões tomadas em 27/10/2026 para o C2 e refinadas até o C3.

## 1. Visão geral

O WatchMatch é um **site independente** (não é extensão da Netflix/Prime). O backend segue uma arquitetura de **microsserviços**: cada parte é um programa separado, com deploy próprio, que se comunica com os outros via HTTP/JSON.

```
frontend ─▶ gateway ─▶ recommendation-service ─┬─▶ llm-service ─────▶ Groq
                   │                            └─▶ catalog-service ─▶ TMDB
                   ├─▶ catalog-service (detalhes de título)
                   └─▶ feedback-service ─▶ Postgres (C3)
```

O `llm-service` **não** tem rota pública: só é acessível pela rede interna.

## 2. Serviços

| Serviço | Responsabilidade | Dono de | Entrega |
| ------- | ---------------- | ------- | ------- |
| **gateway** | Porta única do site: roteamento, rate limit por IP, limite de tamanho do corpo, geração do `X-Request-Id` | nada | C2 |
| **recommendation-service** | Orquestrador do fluxo de recomendação (seção 4) | nada (stateless) | C2 |
| **llm-service** | Integração com o Groq: `POST /intent` e `POST /rerank`; prompts versionados; validação do JSON do LLM | chave do Groq | C2 |
| **catalog-service** | Integração com o TMDB: discover, detalhes, onde assistir, gêneros e plataformas do BR; cache | chave do TMDB | C2 |
| **feedback-service** | "Gostei"/"Não gostei"/"Vou assistir" e métricas de aceitação | banco Postgres próprio | C3 |

Princípios:

- Cada serviço é **dono dos seus segredos e dados**; nenhum serviço acessa o banco ou a API externa de outro.
- Comunicação **síncrona REST/JSON**. Fila (RabbitMQ/Kafka) só se fizer sentido no C3 (ex.: eventos de feedback).

## 3. Linguagens e liberdade de escolha

**Padrão:** Java 21 + Spring Boot 4 (testes com JUnit 5, Mockito e o `MockRestServiceServer` do Spring no lugar das APIs externas).
**Gateway:** Nginx (configuração, sem código). Pode virar Spring Cloud Gateway se precisar de lógica própria.

Cada desenvolvedor **pode escolher outra linguagem** para o serviço que implementa, desde que cumpra os combinados abaixo. O serviço é uma caixa-preta: vale o que ele promete por fora.

### 3.1 Contrato primeiro

- Antes do código, o serviço ganha `backend/contracts/<servico>.yaml` (**OpenAPI 3**) com rotas, entradas e saídas.
- O contrato é a fonte da verdade; qualquer implementação que o cumpra é válida.
- **Prism** gera um mock do serviço a partir do contrato, permitindo desenvolver os outros serviços e o frontend antes da implementação real.
- Contratos atuais: `common.yaml` (esquemas compartilhados), `catalog-service.yaml`, `llm-service.yaml` e `recommendation-service.yaml`. Validação: `npx @redocly/cli lint` dentro de `backend/contracts/`.
- O que cada API externa oferece e as decisões de modelo/fonte estão em [APIS.md](./APIS.md).

### 3.2 Convenções obrigatórias (todos os serviços)

| Item | Regra |
| ---- | ----- |
| Entrega | `Dockerfile` na pasta do serviço; build com a ferramenta da linguagem dentro do container |
| Configuração | Somente por variáveis de ambiente (`PORT`, `GROQ_API_KEY`, `CATALOG_URL`…), nunca fixa no código |
| Saúde | `GET /health` → `200` `{"status": "ok"}` quando o serviço está de pé. O `docker-compose.yml` usa essa rota nos healthchecks e só sobe quem depende do serviço depois que ele responde |
| Rastreamento | Recebe e repassa o cabeçalho `X-Request-Id` em toda chamada |
| Erros | `application/problem+json`: `{ "status", "title", "detail" }` |
| Logs | JSON na saída padrão, incluindo `requestId` |
| Timeouts | Toda chamada HTTP a outro serviço ou API externa tem timeout definido |
| README | Linguagem escolhida, motivo, como rodar e testar |

### 3.3 Testes de contrato

**Schemathesis** lê o OpenAPI e dispara requisições contra o serviço rodando, verificando se as respostas batem com o contrato — o mesmo teste vale para qualquer linguagem. Roda no GitHub Actions para cada serviço.

## 4. Fluxo de uma recomendação

```
frontend ──POST /api/recommend {texto, filtros}──▶ gateway ──▶ recommendation-service
  1. Valida e sanitiza o texto (máx. ~500 caracteres)
  2. llm-service /intent: texto → JSON {tipo, gêneros, excluir_gêneros, duração_mín, duração_máx,
     anos, idioma, clima/palavras-chave, título_referência}
  3. Mescla com os filtros explícitos (filtro da UI sempre vence o LLM)
  4. catalog-service /discover (BR + plataformas) → ~30–40 candidatos reais
     (ou /similar quando há "parecido com X"; se nada aparece, afrouxa aos poucos)
  5. llm-service /rerank: recebe SÓ os candidatos (id, título, sinopse) →
     escolhe os títulos da página (12, 24, 36 ou 48) + justificativa curta de cada
  6. Grounding: descarta qualquer id que não esteja entre os candidatos; o que faltar
     para encher a página vem na ordem do catálogo
  7. Resposta: critérios interpretados + cards (pôster, ano, duração, onde assistir, motivo)
```

**Critérios no TMDB.** Duração mínima e idioma original viram filtros diretos. As palavras-chave (em
inglês, até 5) viram ids do TMDB (`/search/keyword`, com cache) e entram com **OU**: "E" entre todas quase
sempre zera a busca nas plataformas do usuário. Palavra sem correspondência é ignorada.

**"Parecido com X" (modelo híbrido, `POST /similar` do catalog-service).**

1. Acha X pelo nome (`/search/multi`), em filmes e séries.
2. Primeiro vêm as recomendações do TMDB para X que estão nas plataformas do usuário (assinatura, grátis
   ou com anúncios) e respeitam os critérios. O TMDB não filtra recomendação por plataforma, então o
   catálogo confere uma a uma, em paralelo (~20 consultas por página do TMDB).
3. O resto da página vem de um discover pelos gêneros de X e até 8 palavras-chave dele.

O próprio X nunca aparece. Sem tipo pedido, os resultados são do tipo de X. "Série parecida com um filme"
usa só as palavras-chave, porque filme e série têm ids de gênero diferentes no TMDB. A referência fica em
cache, porque "Carregar mais" pede o mesmo X de novo. Se X não existe, aparece o aviso "Não achei X no
catálogo" e a busca segue pelo resto do pedido.

**Afrouxar aos poucos.** Se os critérios zeram a busca, o recommendation-service tenta de novo sem as
palavras-chave, depois sem idioma e duração mínima, e só então sem nenhum critério da IA. Cada passo só tira
o que veio da IA: o que o usuário marcou na tela fica. O aviso diz o que foi deixado de lado ("deixei de
lado: palavras-chave"). São até 3 buscas a mais no TMDB, e só quando a primeira volta vazia.

**Tamanho da página.** Só múltiplos de 12 (`limit` ∈ 12, 24, 36, 48): a grade tem 2, 3 ou 4 colunas
conforme a tela, e 12 divide por todas, então a última linha sempre fecha. Quando há mais resultados, a
página vem com exatamente `limit` títulos.

**Modos da IA (`aiMode`).**

| Modo | Passos | Custo no Groq |
| ---- | ------ | ------------- |
| `pick` (padrão) | 1–7: a IA entende, escolhe e justifica | 2 chamadas; o /rerank cresce com a página |
| `filters` | 1–4 e 7: a IA só entende; ordem do catálogo, sem `reason` | 1 chamada pequena |

No modo `pick`, a IA recebe a página + 12 candidatos de folga (de 30 a 60). Quanto mais candidatos,
mais curtas ficam as sinopses enviadas (de 300 a 150 caracteres) e as justificativas pedidas (de 150 a
80). Assim um pedido de 48 cabe nos 8k tokens/min do Groq gratuito. Mesmo assim, duas buscas de 48
seguidas podem estourar o limite; nesse caso a segunda cai na degradação controlada abaixo.

O passo 6 é a mitigação de alucinação e prompt injection descrita em [IA_RESPONSAVEL.md](./IA_RESPONSAVEL.md): o LLM nunca cria títulos, só escolhe entre itens reais do catálogo.

### Resiliência

Toda chamada à IA passa pelo `AiGuard` do recommendation-service (núcleo do Resilience4j, sem o starter, que é
feito para o Spring Boot 3). Se a IA ficar de fora, a resposta sai com os resultados do catálogo **sem
justificativas**, `degraded: true` e um aviso em `notices` dizendo o motivo (degradação controlada).

| Peça | Como funciona |
| ---- | ------------- |
| Timeout | 2 s para conectar; 40 s de leitura no llm-service (48 justificativas levam tempo) |
| Circuit breaker, um para o `/intent` e outro para o `/rerank` | Abre com 50% de falha nas últimas 10 chamadas (mínimo 4). Fica aberto 30 s: a busca nem tenta a IA e responde na hora. Depois deixa passar 2 chamadas de teste; deram certo, fecha. Contam como falha: 5xx (o llm-service devolve 502 quando o Groq falha ou estoura a cota), timeout e erro de conexão. Erro 4xx não conta, porque é bug do nosso pedido |
| Limite suave do `/rerank` | 8 fichas por minuto no site, cobradas pelo tamanho da página: 12 títulos = 1 ficha, 24 = 2, 36 = 3, 48 = 4. Sem ficha, a busca não falha: vem na ordem do catálogo. Acompanha os 8k tokens/min do Groq gratuito |

Os circuitos são separados porque o `/rerank` é o que pesa. Se só ele cair, a IA ainda entende o pedido e a
busca vira o modo `filters`. Os números são ajustáveis por variável de ambiente (`AI_CIRCUIT_OPEN_FOR`,
`AI_RERANK_PERMITS` etc., ver `AiGuardConfig`). Os contadores ficam na memória do serviço.

| Situação | Aviso para o usuário |
| -------- | -------------------- |
| `/intent` falhou | "A IA não respondeu desta vez: a busca usou só os filtros da tela." |
| Circuito do `/intent` aberto | "A IA está fora do ar por alguns instantes: a busca usou só os filtros da tela." |
| `/rerank` falhou ou circuito aberto | "…os títulos vieram na ordem do catálogo, sem justificativa." |
| Sem fichas do limite suave | "Muita gente buscando agora: a IA entendeu o pedido, mas os títulos vieram na ordem do catálogo…" |

**Demonstração do vídeo:** `docker compose stop llm-service`. As primeiras buscas (até 4) tentam a IA e falham, e a partir
daí o circuito abre e as buscas respondem em menos de 1 s, sem IA. Depois `docker compose start llm-service`;
passados 30 s, a IA volta sozinha.

## 5. Gateway

Rotas públicas (tudo o que não está listado é bloqueado):

| Rota pública | Destino |
| ------------ | ------- |
| `POST /api/recommend` | recommendation-service |
| `GET /api/titles/:type/:id` | catalog-service |
| `GET /api/meta` | catalog-service (gêneros e plataformas do BR) |
| `POST /api/feedback` | feedback-service (C3) |
| `GET /health` | gateway |
| qualquer outro caminho fora de `/api` | frontend (o site) |

Responsabilidades centralizadas no gateway: `limit_req` (protege o free tier do Groq), limite de tamanho do corpo
(16 KB) e `X-Request-Id` (`$request_id` do Nginx). CORS não é preciso: site e API saem da mesma origem. Rotas
internas (`/discover`, `/titles/lookup`, o llm-service) respondem 404 para fora. Configuração em
`backend/gateway/nginx.conf`.

### Rate limit

Modelo **por IP + teto global**, só no `/api/recommend`, que é a rota que gasta a IA:

| Zona | Chave | Limite | Folga (cliques seguidos) | Para quê |
| ---- | ----- | ------ | ------------------------ | -------- |
| `recommend_ip` | IP | 6 buscas/min | 4 | um usuário sozinho não esgota a cota diária de todos |
| `recommend_all` | o site todo | 20 buscas/min | 10 | acompanha a cota por minuto do Groq, venha de quantos IPs vier |
| `api_ip` | IP | 10 pedidos/s | 20 | `/api/meta` e detalhes, que não usam IA |

Passou do limite: `429` em `application/problem+json`, com `Retry-After: 10`, e o site mostra o `detail`
("Muitas buscas seguidas…"). Os contadores ficam na memória do Nginx e zeram quando ele reinicia.

Modelos avaliados e descartados por ora: só por IP (não protege a cota com muitos usuários e barra uma sala
inteira atrás do mesmo Wi-Fi); por custo em tokens, com Bucket4j (mais justo, mas exige código e contadores);
cota diária por IP (exige Redis ou Postgres). Abaixo do gateway há ainda o limite **suave** do recommendation-service (seção 4, "Resiliência"): quando o
Groq ou as fichas apertam, a busca cai para o modo `filters` em vez de falhar.

No deploy atrás de um proxy (Render, Railway), o Nginx precisa do `real_ip` para enxergar o IP do usuário;
o trecho está comentado no `nginx.conf`.

## 6. Estrutura do repositório

```
backend/
  gateway/                   nginx.conf · Dockerfile
  recommendation-service/
  llm-service/               (inclui prompts/ versionados)
  catalog-service/
  feedback-service/          (C3)
  contracts/                 OpenAPI de cada serviço
  eval/                      casos e script de eval (chama o gateway via HTTP)
  .env.example               GROQ_API_KEY, TMDB_API_KEY
frontend/                    Dockerfile + nginx.conf (só serve o site; o gateway repassa para ele)
docker-compose.yml           sobe tudo com `docker compose up --build` (docs/COMO_RODAR.md)
docs/
  ARQUITETURA.md · EVAL.md
  prompts/                   registro do uso de IA no desenvolvimento (exigido pelo edital)
trash/                       lixeira local, ignorada pelo git
```

## 7. Frontend

Vite + React 19 + TypeScript + Tailwind CSS. O frontend conhece **apenas `/api`**: em desenvolvimento quem
repassa é o proxy do Vite (sem rate limit); no Docker, o gateway.

### Telas

1. **Home / busca** — campo grande ("O que você quer assistir hoje?"), chips de exemplo, seleção das plataformas assinadas (salva no `localStorage`, sem cadastro) e painel de filtros recolhível (filme/série, gênero, duração, ano, classificação, idioma).
2. **Resultados** — critérios interpretados em chips editáveis ("Entendi: comédia · até 2h · Netflix") e grade de cards com o motivo da IA, logos das plataformas e "Gostei"/"Não gostei"/"Vou assistir".
3. **Detalhe** (gaveta/modal) — sinopse, elenco, classificação, onde assistir, "mais parecidos".
4. **Sobre / Privacidade** — limitações, fontes e **atribuição obrigatória ao TMDB e ao JustWatch**.
5. *(C3, opcional)* "Minha lista" no `localStorage` e página interna com resultados do eval.

## 8. Testes e eval

| Nível | Ferramenta | O que cobre |
| ----- | ---------- | ----------- |
| Unitário | JUnit 5 + Mockito (ou equivalente da linguagem) | sanitização, mescla de filtros, parse do LLM, grounding |
| Integração | `MockRestServiceServer` (Groq/TMDB simulados) | cada serviço isolado contra APIs externas falsas |
| Contrato | Schemathesis | cada serviço cumpre seu OpenAPI |
| Ponta a ponta | Docker Compose + chamadas ao gateway | fluxo completo, incluindo fallback sem LLM |

**Eval do componente de IA** ([backend/eval/](../backend/eval/README.md)): 23 casos em `cases.json` com a frase e o esperado (16 normais e 7 difíceis: gíria, frase vaga, pedido impossível, prompt injection). Um script Python chama o gateway e confere o entendimento (critérios que a IA tirou da frase) e o resultado (cada título está na plataforma e respeita os critérios). Dá uma nota em vez de passar ou falhar; cada rodada fica salva em `backend/eval/results/`.

| Métrica | Meta |
| ------- | ---- |
| JSON válido na saída do LLM | ~100% |
| Recomendações disponíveis na plataforma pedida | ~100% |
| Critérios respeitados (gênero, duração, ano) | alto |
| Títulos fora do catálogo de candidatos | 0 |
| Latência por requisição | registrar (p50/p95) |

## 9. Plano até o C2 (30/10/2026)

| Dia | Backend | Frontend |
| --- | ------- | -------- |
| **28/10** | Contratos OpenAPI; `docker-compose.yml`; gateway Nginx; catalog-service | Projeto, Home, seleção de plataformas, cards usando mock do Prism |
| **29/10** | llm-service (intent + rerank); recommendation-service com grounding e fallback; testes principais | Integração com o gateway, chips de critérios, gaveta de detalhe |
| **30/10** | 5–10 casos de eval; ajustes; gravação do vídeo | |

**Roteiro do vídeo:** problema → arquitetura (diagrama) → 3 buscas ao vivo → critérios interpretados → derrubar o llm-service e mostrar o fallback → eval rodando.

**Fica para o C3:** feedback-service + Postgres, deploy na nuvem (avaliar free tier com vários serviços vs. VM rodando o Compose), Playwright, Watchmode, PWA, possível reescrita de um serviço em outra linguagem (ex.: catalog-service em C++).

## 10. Pendências

- Adicionar o professor como colaborador no GitHub (exigência do C2).
- Criar as chaves gratuitas do Groq e do TMDB e preencher o `.env` (nunca versionar).
