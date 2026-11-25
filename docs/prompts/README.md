# Registro de uso de IA no desenvolvimento

O edital pede que os prompts usados para gerar ou auxiliar código e documentação fiquem registrados no repositório.
Este registro cobre **o desenvolvimento** do WatchMatch. Os prompts que o **produto** manda ao Groq em produção são
outra coisa: ficam versionados em `backend/llm-service/src/main/resources/prompts/` e são medidos pelo eval.

## Como está organizado

- [REGISTRO.md](REGISTRO.md): uma entrada por funcionalidade ou fase, em ordem de data, com:
  - **Ferramenta**: qual assistente de IA foi usado;
  - **Pedido**: o prompt (com as palavras de quem pediu, quando registradas, ou um resumo fiel);
  - **O que a IA fez**: código, testes ou documentação produzidos;
  - **Decisão e revisão humana**: o que a equipe escolheu entre as opções apresentadas, o que mudou ou rejeitou;
  - **Commits**: onde o resultado entrou no repositório.

## Como trabalhamos com a IA

- **A equipe decide; a IA propõe.** Nas decisões de projeto (modelo de rate limit, circuit breaker, filtros de
  gênero, layout), a IA apresentava opções com prós e contras e uma recomendação, e a equipe escolhia. As escolhas
  estão no registro.
- **Nada vai ao Git sem revisão.** A IA preparava os comandos de commit; quem commitava e enviava era a equipe, depois
  de revisar e testar.
- **Testado antes de aceitar.** Cada mudança passou pelos testes automatizados de cada serviço e, quando mexia na IA ou
  no catálogo, por testes contra o TMDB e o Groq de verdade ou pelo eval.
- **Chaves nunca nos prompts.** As chaves do TMDB e do Groq ficam no `backend/.env` e não foram coladas em conversas.

## O que falta completar

O registro foi montado a partir do histórico de commits e das conversas que ficaram disponíveis. As entradas
marcadas com **"a completar"** cobrem as primeiras fases do projeto, em que a equipe deve acrescentar a ferramenta e
os pedidos que usou.
