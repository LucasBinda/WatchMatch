# Análise de impacto

O WatchMatch recebe um pedido em linguagem natural ("um terror curto pra hoje à noite") e devolve filmes e séries
**disponíveis nas plataformas que a pessoa já assina** no Brasil, com o motivo de cada sugestão. Este documento
analisa para quem o projeto serve, o que ele entrega, que riscos traz e como esses riscos foram medidos e tratados.
As práticas de privacidade, segurança e transparência estão em [IA_RESPONSAVEL.md](IA_RESPONSAVEL.md); os números
vêm do eval ([backend/eval/](../backend/eval/README.md)).

## 1. Problema e público

- **Quem usa:** quem assina um ou mais serviços de streaming no Brasil e perde tempo escolhendo o que ver,
  pulando de aplicativo em aplicativo. O catálogo de cada plataforma muda com frequência e nenhuma delas mostra o
  que está nas outras.
- **O que muda para essa pessoa:** um lugar só para pedir do jeito que fala ("pra ver com meu filho de 6 anos",
  "parecido com Interestelar"), com resultados filtrados pelo que ela de fato pode assistir sem pagar a mais
  (assinatura, grátis ou com anúncios; aluguel e compra ficam de fora).
- **Quem mais é afetado:** as fontes de dados (TMDB, que reúne os dados do JustWatch sobre onde assistir), o
  provedor de IA (Groq) e as plataformas de streaming, que recebem tráfego direcionado sem que o WatchMatch
  copie seu catálogo.

## 2. Benefícios entregues

| Benefício | Como o produto entrega |
| --------- | ---------------------- |
| Menos tempo escolhendo | Uma busca reúne todas as plataformas escolhidas; o botão "Abrir na plataforma" leva direto à busca do título |
| Pedido do jeito que se fala | A IA transforma a frase em filtros (gênero, duração, ano, idioma, classificação, "parecido com X") |
| Controle de quem usa | Os filtros da tela sempre vencem a IA; tudo que a IA entendeu aparece como chip, com o selo "IA", e pode ser tirado com um X |
| Sem cadastro | Nenhuma conta, nenhum dado pessoal guardado no servidor (seção 5) |
| Funciona sem a IA | Se o Groq falha ou está no limite, a busca segue só com os filtros e o site avisa o motivo |

## 3. Evidências: o que o eval mediu

O eval roda 28 frases contra o sistema inteiro (pelo gateway, com o Groq de verdade): 16 normais, 7 difíceis
(gíria, frase vaga, pedido impossível, prompt injection) e 5 de generalização (sinais que o prompt não cita, como
"dorama" ou "Bollywood"). Cada rodada fica salva em `backend/eval/results/`.

| Métrica | intent.v2 | intent.v3 | v3 + rerank enxuto |
| ------- | --------- | --------- | ------------------ |
| Casos com entendimento certo | 22–23/28 (79–82%) | 27/28 (96%) | 28/28 (100%) |
| Conferências de entendimento certas | 64/70 (91%) | 68–69/70 (99%) | 70/70 (100%) |
| Títulos disponíveis na plataforma do usuário | 100% | 100% | 100% |
| Títulos que respeitam os critérios | 97–100% | 97–98% | 324/325 |
| **Títulos fora do catálogo (inventados)** | **0** | **0** | **0** |
| Tentativas de prompt injection resistidas | 2/2 | 2/2 | 2/2 |
| Latência p50 / p95 | 1,4–1,6 s / 1,8–3,4 s | 1,2 s / 1,8 s | 3,4 s / 4,3 s |

As duas primeiras colunas medem só o entendimento (modo `filters`); a última mede o fluxo completo, com a IA
escolhendo e justificando os títulos, por isso é mais lenta.

O que o eval mostrou e foi corrigido no caminho:

- **Filtro de duração frouxo no TMDB.** 32 de 60 animações "até 90 min" passavam do limite. Hoje o catálogo
  confere a duração pela ficha de cada título.
- **Cota do Groq estourando.** O rerank gastava ~4.000 tokens por busca, e o plano grátis aceita 8.000 por minuto:
  no máximo ~2 buscas por minuto no site inteiro. O prompt `rerank.v3` corta para ~2.000 tokens.
- **Sinais implícitos.** "Anime japonês" ou "filho de 6 anos" não viravam idioma e classificação. O `intent.v3`
  subiu o entendimento de ~80% para 96%, inclusive nos casos de generalização.
- **Séries sem gênero de filme.** O TMDB não tem Terror, Romance nem Thriller para séries; "série de terror"
  mostrava séries populares quaisquer. Hoje esses gêneros viram palavras-chave do TMDB em séries.

## 4. Riscos e como foram tratados

| Risco | Impacto | Tratamento | Situação |
| ----- | ------- | ---------- | -------- |
| IA inventar título ("alucinação") | Pessoa procura algo que não existe ou não está na plataforma dela | A IA só escolhe entre candidatos que o catálogo trouxe; ids fora da lista são descartados (grounding) | 0 inventados em todas as rodadas |
| Prompt injection | Usar o site para outra coisa ou extrair o prompt | Texto isolado entre marcadores, saída em JSON com schema estrito e ids permitidos, sanitização e limite de 500 caracteres | Resistiu nos casos do eval |
| Classificação indicativa errada | Conteúdo impróprio num pedido para criança | Filtro de classificação no TMDB para filmes | **Limitação:** o TMDB não filtra classificação de séries |
| Viés de popularidade | Títulos de nicho e independentes quase não aparecem | Ordenação por popularidade e corte de 50 votos evitam títulos sem dados; a IA reordena por adequação ao pedido | Aceito e comunicado no rodapé |
| Viés de idioma e região | Produções com poucos dados em português ficam de fora | Busca e fichas em pt-BR, região BR; filtro por idioma original | Parcial |
| Dados desatualizados | Título já saiu da plataforma | Fichas em cache por no máximo 6 h; aviso no rodapé | Depende do JustWatch |
| Abuso e custo | Robôs gastando a cota do Groq | Rate limit no gateway (6 buscas/min por IP e teto global), limite suave de fichas e circuit breaker | Testado em produção |
| Dependência de terceiros | TMDB ou Groq fora do ar | Sem Groq: busca só com filtros e aviso. Sem TMDB: erro claro (não há recomendação possível) | Testado derrubando o llm-service |
| Disponibilidade do site | Fora do ar na hora da avaliação | Link fixo que avisa quando o site está desligado ([DEPLOY.md](DEPLOY.md)) | **Limitação:** roda no computador da equipe |

## 5. Privacidade

- **Sem conta e sem banco de dados.** As preferências (plataformas, modo da IA, tamanho da página, paleta) ficam
  só no `localStorage` do navegador da pessoa.
- **O texto digitado vai para o Groq**, que processa o pedido, sem nenhum identificador da pessoa. Ele não é
  gravado nos logs dos nossos serviços.
- **O gateway registra IP e caminho** de cada pedido (log padrão do Nginx, sem o texto), necessário para o rate
  limit. Os logs giram e somem sozinhos (até 30 MB por container).
- Detalhes e base legal em [IA_RESPONSAVEL.md](IA_RESPONSAVEL.md).

## 6. Custo e ambiente

| Item | Custo | Observação |
| ---- | ----- | ---------- |
| TMDB | Grátis | Exige atribuição: logo e aviso no rodapé |
| Groq (plano grátis) | Grátis | 8.000 tokens/min e 1.000 pedidos/dia por modelo; ~2 chamadas de IA por busca |
| Hospedagem | Grátis | Computador da equipe + túnel da Cloudflare; Azure, Render e Railway foram descartados ([DEPLOY.md](DEPLOY.md)) |
| Link fixo | Grátis | GitHub Pages, publicado só quando o endereço muda |

Escolhas que reduzem consumo de computação (e de cota):

- **Modelos pequenos onde basta:** `gpt-oss-20b` para entender o pedido; o `gpt-oss-120b` só para escolher e
  justificar os títulos.
- **Modo "Só monta os filtros":** a IA só entende o pedido, sem escolher títulos; metade das chamadas.
- **Caches:** fichas dos títulos (6 h), palavras-chave e títulos de referência. "Carregar mais" não refaz buscas.
- **Prompts enxutos:** o `rerank.v3` usa metade dos tokens do v2.
- **Teto de memória** por container na produção (~1,9 GB no total).

## 7. Acessibilidade e inclusão

- Interface em português, sem cadastro, funcionando no celular (filtros numa janela própria) e com zoom alto
  (a coluna de filtros aparece a partir de 768 px).
- Controles com rótulos para leitor de tela, inclusive o slider de duração, que funciona pelas setas do teclado.
- O seletor de paletas permite trocar para paletas claras ou de maior contraste.
- Dark mode como padrão, pensado para quem escolhe o que ver à noite.

## 8. Limitações conhecidas

- O site só fica no ar com o computador da equipe ligado.
- O Groq grátis aguenta poucas buscas por minuto com a escolha da IA; com muita gente ao mesmo tempo, as buscas
  seguem sem justificativa (com aviso).
- Classificação indicativa não é filtrada em séries (limitação do TMDB).
- Em séries, gêneros que só existem em filme viram palavras-chave, que são menos precisas que gêneros.
- A disponibilidade depende do JustWatch e pode estar desatualizada.
- Não há feedback ("gostei / não gostei") para medir a satisfação de quem usa: o eval mede a IA, não as pessoas.

## 9. Próximos passos

- Feedback dos usuários para medir aceitação das sugestões.
- Domínio próprio e túnel nomeado (ou servidor sempre ligado) para tirar a dependência do computador da equipe.
- Plano pago do Groq, ou fila, se o uso crescer.
- Filtro de classificação para séries cruzando outra fonte de dados.
