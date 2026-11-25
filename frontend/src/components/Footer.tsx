// Logo oficial do TMDB (https://www.themoviedb.org/about/logos-attribution), servido pelo próprio TMDB.
// Os termos da API pedem o logo e o aviso abaixo, com o logo menos destacado que o do WatchMatch
const TMDB_LOGO =
  'https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg'

export function Footer() {
  return (
    <footer className="border-t border-line/70">
      {/* pb-24: espaço para o botão redondo das paletas, que fica fixo no canto e cobriria o fim do texto */}
      <div className="space-y-2 px-4 pt-8 pb-24 text-sm text-muted sm:px-6 lg:px-8">
        <p>Sugestões baseadas em metadados públicos; a disponibilidade pode estar desatualizada.</p>
        <p>
          Dados de filmes e séries:{' '}
          <a href="https://www.themoviedb.org" target="_blank" rel="noopener noreferrer" className="text-brand-soft hover:underline">
            TMDB
          </a>
          . Dados de onde assistir:{' '}
          <a href="https://www.justwatch.com" target="_blank" rel="noopener noreferrer" className="text-brand-soft hover:underline">
            JustWatch
          </a>
          .
        </p>
        <div className="flex flex-wrap items-center gap-3 pt-1">
          <a href="https://www.themoviedb.org" target="_blank" rel="noopener noreferrer" aria-label="The Movie Database (TMDB)">
            <img src={TMDB_LOGO} alt="TMDB" className="h-3 w-auto" />
          </a>
          <p className="text-faint">This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
        </div>
      </div>
    </footer>
  )
}
