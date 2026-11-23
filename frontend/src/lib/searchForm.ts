import { formatRuntime } from './format'
import type { AiMode, AppliedCriteria, Criteria, GenreMatch, MediaType, Meta, PageSize } from '../types'

export type FilterField = 'genres' | 'runtimeMax' | 'mediaType'

export interface SearchForm {
  query: string
  genres: number[] // ids marcados; vazio = qualquer gênero
  genreMatch: GenreMatch // com 2 ou mais gêneros: 'all' exige todos, 'any' aceita qualquer um
  runtimeMax: string // minutos ou ''
  mediaType: string // 'movie' | 'tv' | ''
  providers: number[]
  aiMode: AiMode
  // Campos que a IA preencheu na última busca. Não vão como filtro do usuário:
  // na próxima busca a IA decide de novo. Mexeu no campo, ele vira seu.
  aiFields: FilterField[]
}

export const RUNTIME_OPTIONS = [
  { value: '', label: 'Qualquer duração' },
  { value: '90', label: 'Até 1h30' },
  { value: '120', label: 'Até 2 horas' },
  { value: '150', label: 'Até 2h30' },
]

export const PAGE_SIZES: PageSize[] = [12, 24, 36, 48]

export const AI_MODE_OPTIONS: { value: AiMode; label: string; hint: string }[] = [
  { value: 'pick', label: 'Escolhe e explica', hint: 'A IA escolhe os títulos e diz por que cada um combina' },
  { value: 'filters', label: 'Só monta os filtros', hint: 'A IA traduz o pedido em filtros; mais rápido, sem justificativa' },
]

export const MEDIA_OPTIONS = [
  { value: '', label: 'Filme e série' },
  { value: 'movie', label: 'Só filmes' },
  { value: 'tv', label: 'Só séries' },
]

// A IA pode escolher uma duração fora da lista (ex.: 100 min); aí entra uma opção a mais
export function runtimeOptions(value: string) {
  if (RUNTIME_OPTIONS.some((o) => o.value === value)) return RUNTIME_OPTIONS
  return [...RUNTIME_OPTIONS, { value, label: runtimeLabel(value) }]
}

export function runtimeLabel(value: string): string {
  return RUNTIME_OPTIONS.find((o) => o.value === value)?.label ?? `Até ${formatRuntime(Number(value))}`
}

// Gêneros de série têm ids próprios no TMDB; sem formato escolhido, usamos os de filme
export function genresFor(mediaType: string, meta: Meta) {
  return mediaType === 'tv' ? meta.genres.tv : meta.genres.movie
}

// "Faroeste + Ficção científica" (todos) ou "Faroeste ou Ficção científica" (qualquer um); '' = qualquer gênero
export function genreLabel(form: SearchForm, meta: Meta): string {
  const names = genresFor(form.mediaType, meta)
    .filter((g) => form.genres.includes(g.id))
    .map((g) => g.name)
  if (names.length <= 1) return names[0] ?? ''
  if (names.length > 3) return `${names.length} gêneros (${form.genreMatch === 'all' ? 'todos' : 'qualquer um'})`
  return names.join(form.genreMatch === 'all' ? ' + ' : ' ou ')
}

// Mantém as plataformas e o modo da IA: são preferências do usuário, não da busca
export function clearSearch(form: SearchForm): SearchForm {
  return { ...form, query: '', genres: [], genreMatch: 'all', runtimeMax: '', mediaType: '', aiFields: [] }
}

// O usuário mudou um campo: ele deixa de ser da IA
export function setField(form: SearchForm, field: 'runtimeMax' | 'mediaType', value: string): SearchForm {
  const next = { ...form, [field]: value, aiFields: form.aiFields.filter((f) => f !== field) }
  // Trocar o formato troca a lista de gêneros
  if (field === 'mediaType') {
    next.genres = []
    next.aiFields = next.aiFields.filter((f) => f !== 'genres')
  }
  return next
}

// Marcar, desmarcar ou trocar "todos / qualquer um": os gêneros passam a ser do usuário
export function setGenres(form: SearchForm, genres: number[], genreMatch = form.genreMatch): SearchForm {
  return { ...form, genres, genreMatch, aiFields: form.aiFields.filter((f) => f !== 'genres') }
}

// Só o que o usuário escolheu vai como filtro; o que a IA preencheu fica de fora
export function userFilters(form: SearchForm): Criteria {
  const mine = (field: FilterField) => form[field].length > 0 && !form.aiFields.includes(field)
  const filters: Criteria = {}
  if (mine('mediaType')) filters.mediaType = form.mediaType as MediaType
  if (mine('genres')) {
    filters.genres = form.genres
    if (form.genres.length > 1 && form.genreMatch === 'all') filters.genreMatch = 'all'
  }
  if (mine('runtimeMax')) filters.runtimeMax = Number(form.runtimeMax)
  return filters
}

// Copia para os campos da tela o que a IA entendeu na busca
export function applyAiCriteria(form: SearchForm, criteria: AppliedCriteria, meta: Meta): SearchForm {
  const fromAi = (key: string) => criteria.sources?.[key] === 'ai'
  // O que a IA tinha preenchido antes some; ela decidiu de novo agora
  const next: SearchForm = { ...form, aiFields: [] }
  for (const field of form.aiFields) {
    if (field === 'genres') next.genres = []
    else next[field] = ''
  }

  if (fromAi('mediaType') && criteria.mediaType) {
    // Gênero escolhido pelo usuário que não existe no novo formato: não troca o formato
    const ids = new Set(genresFor(criteria.mediaType, meta).map((g) => g.id))
    const keepsGenre = next.genres.every((g) => ids.has(g))
    if (keepsGenre) {
      next.mediaType = criteria.mediaType
      next.aiFields.push('mediaType')
    }
  }
  if (fromAi('genres') && criteria.genres) {
    // Ficam os que existem na lista do formato. Os gêneros da IA são sempre "qualquer um"
    const ids = new Set(genresFor(next.mediaType, meta).map((g) => g.id))
    const genres = criteria.genres.filter((g) => ids.has(g))
    if (genres.length > 0) {
      next.genres = genres
      next.genreMatch = 'any'
      next.aiFields.push('genres')
    }
  }
  if (fromAi('runtimeMax') && criteria.runtimeMax) {
    next.runtimeMax = String(criteria.runtimeMax)
    next.aiFields.push('runtimeMax')
  }
  return next
}
