# WatchMatch (`notComplete`)

Aplicação web de **recomendação de filmes e séries** com filtros + intenção em linguagem natural (LLM via Groq api), considerando as plataformas disponíveis para o usuário (ex.: Netflix, Amazon Prime Video).



## Ideia em uma frase

Você descreve o que quer assistir (ou usa filtros); a IA interpreta e recomenda títulos **ancorados em catálogo público**, indicando **onde assistir** — sem depender dos filtros limitados de cada streaming.

## Como funciona

1. **Você descreve.** Escreva do seu jeito ou use os filtros: gênero, duração, formato e as plataformas que você assina.
2. **A IA interpreta.** Um modelo de linguagem transforma o pedido em critérios e escolhe, entre títulos reais do catálogo, os que mais combinam.
3. **Você vê onde assistir.** O site mostra **12 sugestões por vez**, ou 24, 36 ou 48, à sua escolha. O botão "Carregar mais" traz as próximas. Cada sugestão vem com o motivo da escolha e as plataformas onde está disponível no Brasil.

Você também escolhe **como a IA ajuda**: ela pode escolher os títulos e explicar cada um (padrão) ou só transformar o pedido em filtros. Nesse caso, os títulos vêm na ordem do catálogo, sem justificativa. É mais rápido e gasta menos da cota da IA.

Sem cadastro: as plataformas escolhidas ficam salvas só no navegador. A IA nunca inventa títulos, ela só escolhe entre itens reais do catálogo.

## Stack

| Camada | Tecnologia |
| ------ | ---------- |
| Frontend | React 19 + TypeScript + Vite + Tailwind CSS. Funciona no navegador do celular |
| Backend | 3 microsserviços em Java 21 + Spring Boot 4 (catálogo, IA e recomendação) |
| IA | GroqCloud (modelos `gpt-oss`) com saída em JSON estruturado e prompts versionados |
| Dados | TMDB: metadados e "onde assistir" no Brasil (dados do JustWatch) |
| Infra | Docker Compose; gateway Nginx na frente de tudo, com rate limit por IP |
| Deploy | VM no Azure com o mesmo Compose e Caddy na frente para o HTTPS ([docs/DEPLOY.md](docs/DEPLOY.md)) |

No relatório do C1 a stack era só uma previsão (Node.js ou Python, Next.js). A escolha final e os motivos
estão em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).

> Catálogos oficiais da Netflix/Prime **não** serão baixados nem scrapados (ToS / direitos). Usamos fontes de metadados e “onde assistir” legítimas.

## Como rodar

```bash
cp backend/.env.example backend/.env   # preencha as chaves do TMDB e do Groq
docker compose up --build              # http://localhost:8080
```

Links das chaves, como subir cada parte separada e como rodar os testes: **[docs/COMO_RODAR.md](docs/COMO_RODAR.md)**.

## Estrutura do repositório

```
frontend/            # site em React (ver frontend/README.md)
backend/             # microsserviços, contratos OpenAPI e .env das chaves (ver backend/README.md)
docs/                # relatórios, arquitetura e guia de como rodar
docker-compose.yml   # sobe tudo com um comando
```

## Colaboradores

- Lucas Binda
- Felipe Koscky

## Licença

Ver [LICENSE](LICENSE).
