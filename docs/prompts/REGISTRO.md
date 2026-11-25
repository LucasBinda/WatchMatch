# Registro de uso de IA

Como ler: ver [README.md](README.md). As citações entre aspas são os pedidos como foram escritos; o resto é resumo.
Ferramenta, quando não dito o contrário: **Claude Code** (Anthropic), trabalhando no repositório.

## Primeiras fases — a completar pela equipe

| Data | Fase | Commits | A completar |
| ---- | ---- | ------- | ----------- |
| 18/09 | Proposta e relatório inicial | `70a7d4e` | Ferramentas e pedidos usados no relatório e na ideia |
| 28/10–31/10 | Arquitetura, contratos, catalog-service (TDD), frontend, recommendation e llm-service | `7a87fc2` a `0381c9d` | Ferramentas e pedidos usados no código e no primeiro vídeo |
| 16/11–18/11 | Páginas de 12 a 48, modos da IA, `failed_generation`, Docker Compose, stack real nos docs | `09bacb8` a `2642fb6` | Idem |

## A partir de 18/11

### Healthchecks e testes com Mockito — 18/11 a 19/11

- **Pedido:** plano de cinco itens (healthchecks, gateway, circuit breaker, filtros do catálogo, entregas finais), um
  de cada vez. Sobre o aviso do Mockito nos testes: "Corrigir agora" e "mas o que é esse mockito?".
- **O que a IA fez:** `/health` nos três serviços, healthchecks no Compose, Mockito como javaagent no surefire, e a
  explicação do que é o Mockito e de como testar antes de commitar.
- **Commits:** `f8289b4`, `8a57026`, `7a663a3`.

### Gateway com rate limit — 19/11

- **Pedido:** "comece e vamos decidir juntos como vai ser esse rate limit por ip, pense em vários modelos diferentes
  que podemos usar".
- **O que a IA fez:** comparou modelos (por IP no Nginx, teto global, por custo em tokens com Bucket4j)
  e implementou o escolhido: Nginx na frente de tudo, rotas internas fechadas, erros em `problem+json`.
- **Decisão da equipe:** limite por IP + teto global agora, limite por custo junto com o circuit breaker.
- **Commits:** `bdb34f9`, `da871d3`.

### Circuit breaker na IA — 21/11

- **Pedido:** decidir juntos como tratar a IA fora do ar ou no limite.
- **Decisão da equipe:** dois circuitos separados (entender o pedido e escolher títulos); configuração equilibrada;
  limite suave cobrando fichas pelo tamanho da página; aviso específico para cada motivo.
- **O que a IA fez:** `AiGuard` com Resilience4j, avisos na tela e roteiro da demonstração (derrubar o llm-service).
- **Commits:** `400cf90`, `143c0d5`, `d503fae`.

### Filtros do catálogo e "parecido com X" — 22/11

- **Pedido:** "vamos decidir" (palavras-chave, idioma, duração mínima, busca por título de referência).
- **Decisão da equipe:** palavras-chave com "ou", por id do TMDB; "parecido com X" híbrido (recomendações do TMDB
  conferidas nas plataformas + busca pelos gêneros e palavras de X); afrouxar a busca aos poucos quando não acha nada.
- **Commits:** `90b4aac`, `184e25f` (prompt `intent.v2`), `db49100`, `32ff92e`.

### Eval da IA — 22/11

- **Pedido:** "vamos começar por Eval da IA, ela é um TDD? n entendi m bem".
- **O que a IA fez:** explicou a diferença entre teste e eval; montou 23 casos, o script em Python e a primeira
  rodada. O eval achou três problemas reais (cota do Groq, duração frouxa no TMDB, entendimentos errados).
- **Decisão da equipe:** Python; medir entendimento e resultado; rodar pelo gateway com pausa; ~20 casos mistos.
- **Commits:** `c40bdc9`, `a5b51cb`.

### Duração conferida pela ficha — 22/11

- **Pedido:** atacar o problema "Duração escapando" achado pelo eval.
- **Decisão da equipe:** conferir no catalog-service; limite exato; cache das fichas por 6 horas.
- **Commits:** `a094560`.

### Vários gêneros, chips com X e gêneros proibidos — 22/11 e 23/11

- **Pedido:** "não da para selecionar mais de um genero por vez, para ter um filtro mais especifico exemplo: generos
  dessejados faroeste, sci-fi". Depois: "Como se chama as caixas adicionadas com o filtro marcado no menu principal que
  poderia ter um X nelas para o usuario rapidamente remover esse em especifico".
- **Decisão da equipe:** "todos" ou "qualquer um"; lista com caixas de marcar, sem limite de gêneros e com aviso
  quando não acha nada; o X só tira do formulário ("Buscar de novo"); botão ⊘ para proibir gêneros.
- **Commits:** `7345755`, `ea4fdfd`. No teste, a IA achou o gateway dando 502 ao recriar um container: `15be3d8`.

### Prompt `intent.v3` — 23/11

- **Pedido:** melhorar o entendimento dos casos que o eval errou ("anime japonês", "filho de 6 anos").
- **Decisão da equipe:** regras de sinais implícitos + poucos exemplos; exemplos diferentes das frases do eval e casos
  novos de generalização, para não "ensinar para a prova"; versão escolhida por variável e comparação só do
  entendimento (v2 × v3, duas rodadas cada). Resultado: de ~80% para 96%.
- **Commits:** `6ef7840`, `a094d2c`.
- **Outra sessão do Claude Code** fez o `rerank.v3` e o tratamento do Groq no limite: `79556e8`, `8e27578`, `5d32bb9`.

### Novo layout e paletas — 25/11

- **Pedido:** "eu quero mudar o frontend do projeto, a tela principal dele é muito feia, vamos criar um novo design com
  nva paleta de cores sem quebrar as funcionalidades existentes".
- **Decisão da equipe:** em vez de escolher uma das três paletas propostas, "Vamos sair trocando aleatoriamente entre
  varias paletas de cores, consegue criar um metodo para isso?"; filtros "na esquerda assim como usam em marketplaces
  como mercado livre"; cards com pôster em destaque. Depois, a paleta padrão "Noite magenta" saiu do gerador
  aleatório; o seletor deixou de trocar fontes e passou a aparecer para todos.
- **Commits:** `c91498c`, `52fe22d`. A página na largura toda (`220471d`) veio de outra sessão.

### Gêneros entre filme e série — 25/11

- **Pedido:** "quando eu mud de categroia de filme e serie para filme parece que mantemos os mesmos generos, mas ele
  deseleciona tudo". Depois: "formatos filmes e series e series estão quebrados está vindo pouca ou quase nenhuma
  opção".
- **Decisão da equipe:** uma lista única de gêneros, traduzida pelo backend; gêneros sem equivalente aparecem
  esmaecidos. A IA testou na produção, achou a causa (séries de terror, romance e thriller não existiam no TMDB) e
  trocou esses gêneros por palavras-chave.
- **Commits:** `f173418`, `efd7b8b`.

### Slider de duração e telas com zoom — 25/11

- **Pedido:** "vamos mudar de duraçao maxima apra duraçao minima E maxima isso funcionaria na api do tmdb? e com a ia?
  vamos pre isar transformar em um slider". Depois: "o slider de duração nao eta funcionando muito bem para series,
  faça alguns testes" e "se nos dermos muito zoom hoje, os filtroos somem".
- **Decisão da equipe:** "um slider que vai de 0 a 4h+"; pontas abertas; atalhos prontos.
- **O que a IA achou:** o TMDB quase não preenche mais a duração de episódio; passou a usar a mediana da 1ª temporada.
- **Commits:** `6e57cd1`, `fc311de`, `9dc3982`.

### Deploy e link fixo — 23/11 a 25/11

- **Pedido:** "Fechar o deploy"; depois "quando eu derrube os containers (o site) ele continua com o link velho do
  cloudflare" e "se fazermos um deploy novo toda vez está realmente certo? vams acabar spámmando com deplo".
- **O que a IA fez:** outra sessão montou o Compose de produção (primeiro com Caddy numa VM, depois com o túnel da
  Cloudflare); nesta, o link fixo no GitHub Pages, a página que confere se o site está no ar, o `deploy.sh parar` e a
  publicação só quando o endereço muda. Explicou que isso é um contorno e qual seria a prática usual (domínio + túnel
  nomeado).
- **Decisão da equipe:** link fixo no GitHub Pages; manter assim para a entrega.
- **Commits:** `57a607b`, `8513d02`, `1a90612`, `84b34e0`.

### Análise de impacto e este registro — 25/11

- **Pedido:** "Sim, análise de impacto".
- **O que a IA fez:** `ANALISE_IMPACTO.md` com os números do eval, a versão final do `IA_RESPONSAVEL.md` e este
  registro, a partir do histórico de commits e das conversas.
- **Decisão da equipe:** documento próprio para o impacto + `IA_RESPONSAVEL.md` atualizado; registro em `docs/prompts/`.
