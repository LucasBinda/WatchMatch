# Frontend — WatchMatch

Interface web em **React + TypeScript**, com **Vite** e **Tailwind CSS**. Conversa só com o gateway (`/api/...`).

## Como rodar

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # checa os tipos e gera dist/
npm run lint
```

## Modo demonstração (dados de exemplo)

`npm run dev` usa um **backend falso** (`src/api/mockApi.ts`), útil quando os serviços não estão rodando:

- os títulos são **reais**, gerados do TMDB e salvos em `src/mocks/catalog.json`;
- os filtros (plataforma, formato, gênero, duração) funcionam;
- o texto livre **não** é interpretado e as justificativas são montadas por modelo.

## Com o backend de verdade

Suba os três serviços (ver `backend/README.md`) e rode:

```bash
npm run dev:backend   # o mesmo que VITE_USE_MOCKS=false
```

Em desenvolvimento o gateway não entra (nem o rate limit): o Vite repassa `/api/recommend` para o recommendation-service
(`localhost:8082`) e o resto de `/api` para o catalog-service (`localhost:8081`). Ver `vite.config.ts`.

## Estrutura

```
src/
  App.tsx                 estado da página e busca
  types.ts                tipos espelhando os contratos em backend/contracts/
  api/client.ts           acesso ao gateway (ou ao mock)
  api/mockApi.ts          backend falso para desenvolvimento
  mocks/catalog.json      títulos e plataformas reais do TMDB
  lib/format.ts           formatação (duração, classificação, chips "Entendi")
  lib/searchForm.ts       estado do formulário de busca e opções dos filtros
  lib/useModalDialog.ts   abre/fecha um <dialog> nativo a partir do estado
  lib/palettes.ts         paletas de cor e gerador (seletor de paletas)
  components/
    Header · SearchBox · FilterChips · FilterPanel · GenreList · RuntimeSlider · PlatformPicker
    FiltersDialog (filtros no celular) · ResultsPanel · CriteriaChips · TitleCard · TitleDetails
    PaletteSwitcher · Footer
```

## Trocar a paleta de cores

Todas as cores passam pelos tokens `--color-*` do `src/index.css`. O botão redondo no canto inferior direito
aparece para todos: troca a paleta na hora, entre 13 prontas ou uma gerada ao acaso, lembra a escolha neste
navegador e troca também a cor do favicon. Em desenvolvimento ele mostra ainda "Copiar cores", que copia os
tokens para colar no `@theme` do `index.css` (a paleta padrão, que é o que todo mundo vê primeiro).

## Pendências

- Aba "Minha lista" (salva no navegador).
