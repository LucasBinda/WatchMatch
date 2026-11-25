import type { Meta } from '../types'

// Uma lista só de gêneros para filme e série. O TMDB tem duas, com ids diferentes; o catalog-service traduz
// cada id para o tipo buscado (backend/catalog-service/.../GenreMap.java, a mesma tabela que esta):
//   - Ação 28 e Aventura 12 → Action & Adventure 10759 em série;
//   - Ficção científica 878 e Fantasia 14 → Sci-Fi & Fantasy 10765; Guerra 10752 → War & Politics 10768;
//   - Terror, Romance, Thriller, História e Música não são gênero em série: lá viram palavra-chave do TMDB;
//   - Kids 10762 (só série) vira Família 10751 em filme.
// Os ids da lista são os de filme, e os de série para os gêneros que só existem em série (Kids, Reality...)
const MOVIE_TO_TV: Record<number, number> = { 28: 10759, 12: 10759, 878: 10765, 14: 10765, 10752: 10768 }
const TV_TO_MOVIE: Record<number, number[]> = { 10759: [28, 12], 10765: [878, 14], 10768: [10752] }
const TV_BY_KEYWORD = new Set([27, 10749, 53, 36, 10402])
const MOVIE_BY_GENRE = new Set([10762])

export interface UnifiedGenre {
  id: number
  name: string
  movie: boolean // existe em filmes
  tv: boolean // existe em séries
}

export function allGenres(meta: Meta): UnifiedGenre[] {
  const movieIds = new Set(meta.genres.movie.map((g) => g.id))
  const tvIds = new Set(meta.genres.tv.map((g) => g.id))
  const fromMovies = meta.genres.movie.map((g) => ({
    id: g.id,
    name: g.name,
    movie: true,
    tv: tvIds.has(g.id) || g.id in MOVIE_TO_TV || TV_BY_KEYWORD.has(g.id),
  }))
  const tvOnly = meta.genres.tv
    .filter((g) => !movieIds.has(g.id) && !(g.id in TV_TO_MOVIE))
    .map((g) => ({ id: g.id, name: g.name, movie: MOVIE_BY_GENRE.has(g.id), tv: true }))
  return [...fromMovies, ...tvOnly].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
}

// Gêneros que vêm do backend com o id de série (a IA pode escolher Sci-Fi & Fantasy) viram os da lista
export function canonicalGenres(ids: number[]): number[] {
  return [...new Set(ids.flatMap((id) => TV_TO_MOVIE[id] ?? [id]))]
}

// '' = filme e série: vale se existir em pelo menos um
export function availableFor(genre: UnifiedGenre, mediaType: string): boolean {
  if (mediaType === 'movie') return genre.movie
  if (mediaType === 'tv') return genre.tv
  return true
}

export function unavailableHint(genre: UnifiedGenre, mediaType: string): string | null {
  if (availableFor(genre, mediaType)) return null
  return mediaType === 'tv' ? 'O TMDB não tem esse gênero em séries' : 'O TMDB não tem esse gênero em filmes'
}
