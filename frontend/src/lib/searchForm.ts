import { allGenres, canonicalGenres } from './genres'
import { formatRuntime } from './format'
import type { AiMode, AppliedCriteria, Criteria, GenreMatch, MediaType, Meta, PageSize } from '../types'

export type FilterField = 'genres' | 'excludeGenres' | 'runtimeMax' | 'mediaType'

export interface SearchForm {
  query: string
  genres: number[] // ids marcados; vazio = qualquer gênero
  genreMatch: GenreMatch // com 2 ou mais gêneros: 'all' exige todos, 'any' aceita qualquer um
  excludeGenres: number[] // gêneros proibidos ("nada de terror")
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


// "Faroeste + Ficção científica" (todos) ou "Faroeste ou Ficção científica" (qualquer um); '' = qualquer gênero
export function genreLabel(form: SearchForm, meta: Meta): string {
  const names = allGenres(meta)
    .filter((g) => form.genres.includes(g.id))
    .map((g) => g.name)
  const banned = allGenres(meta)
    .filter((g) => form.excludeGenres.includes(g.id))
    .map((g) => `sem ${g.name.toLowerCase()}`)
  const wanted = wantedLabel(names, form.genreMatch)
  return [wanted, ...banned].filter(Boolean).join(', ')
}

function wantedLabel(names: string[], genreMatch: GenreMatch): string {
  if (names.length <= 1) return names[0] ?? ''
  if (names.length > 3) return `${names.length} gêneros (${genreMatch === 'all' ? 'todos' : 'pelo menos um'})`
  return names.join(genreMatch === 'all' ? ' + ' : ' ou ')
}

export type FilterChip =
  | { kind: 'mediaType'; label: string; ai: boolean }
  | { kind: 'genre'; id: number; label: string; ai: boolean }
  | { kind: 'excludeGenre'; id: number; label: string; ai: boolean }
  | { kind: 'runtimeMax'; label: string; ai: boolean }

// Os filtros do formulário como chips da página principal (cada um com X)
export function filterChips(form: SearchForm, meta: Meta): FilterChip[] {
  const ai = (field: FilterField) => form.aiFields.includes(field)
  const genres = allGenres(meta)
  const chips: FilterChip[] = []
  if (form.mediaType) {
    chips.push({ kind: 'mediaType', label: form.mediaType === 'movie' ? 'Filmes' : 'Séries', ai: ai('mediaType') })
  }
  for (const g of genres.filter((g) => form.genres.includes(g.id))) {
    chips.push({ kind: 'genre', id: g.id, label: g.name, ai: ai('genres') })
  }
  for (const g of genres.filter((g) => form.excludeGenres.includes(g.id))) {
    chips.push({ kind: 'excludeGenre', id: g.id, label: `sem ${g.name.toLowerCase()}`, ai: ai('excludeGenres') })
  }
  if (form.runtimeMax) chips.push({ kind: 'runtimeMax', label: runtimeLabel(form.runtimeMax), ai: ai('runtimeMax') })
  return chips
}

// O X de um chip: tira só aquele filtro do formulário. A busca roda quando o usuário pedir
export function removeFilter(form: SearchForm, chip: FilterChip): SearchForm {
  switch (chip.kind) {
    case 'genre':
      return setGenreFilter(form, { genres: form.genres.filter((g) => g !== chip.id) })
    case 'excludeGenre':
      return setGenreFilter(form, { excludeGenres: form.excludeGenres.filter((g) => g !== chip.id) })
    case 'runtimeMax':
      return setField(form, 'runtimeMax', '')
    case 'mediaType':
      return setField(form, 'mediaType', '')
  }
}

// Mantém as plataformas e o modo da IA: são preferências do usuário, não da busca
export function clearSearch(form: SearchForm): SearchForm {
  return { ...clearFilters(form), query: '' }
}

// Só os filtros (o "Limpar" dos chips): o texto da busca fica
export function clearFilters(form: SearchForm): SearchForm {
  return { ...form, genres: [], genreMatch: 'all', excludeGenres: [], runtimeMax: '', mediaType: '', aiFields: [] }
}

// Tudo que muda o resultado. Diferente da última busca = mostrar "Buscar de novo"
export function searchKey(form: SearchForm): string {
  const { query, genres, genreMatch, excludeGenres, runtimeMax, mediaType, providers, aiMode } = form
  return JSON.stringify([query.trim(), genres, genreMatch, excludeGenres, runtimeMax, mediaType, providers, aiMode])
}

// O usuário mudou um campo: ele deixa de ser da IA
export function setField(form: SearchForm, field: 'runtimeMax' | 'mediaType', value: string): SearchForm {
  const next = { ...form, [field]: value, aiFields: form.aiFields.filter((f) => f !== field) }
  // Trocar o formato troca a lista de gêneros
  // Trocar o formato não mexe nos gêneros: a lista é uma só (lib/genres.ts) e o catálogo traduz
  return next
}

export interface GenreChange {
  genres?: number[]
  excludeGenres?: number[]
  genreMatch?: GenreMatch
}

// Marcar, proibir, tirar ou trocar "todos / pelo menos um": o que mudou passa a ser do usuário.
// Um gênero nunca fica pedido e proibido ao mesmo tempo: o último clique vence
export function setGenreFilter(form: SearchForm, change: GenreChange): SearchForm {
  const next = { ...form, ...change }
  if (change.genres) next.excludeGenres = next.excludeGenres.filter((g) => !change.genres!.includes(g))
  if (change.excludeGenres) next.genres = next.genres.filter((g) => !change.excludeGenres!.includes(g))
  const touched: FilterField[] = []
  if (change.genres || change.genreMatch || next.genres.length !== form.genres.length) touched.push('genres')
  if (change.excludeGenres || next.excludeGenres.length !== form.excludeGenres.length) touched.push('excludeGenres')
  next.aiFields = form.aiFields.filter((f) => !touched.includes(f))
  return next
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
  if (mine('excludeGenres')) filters.excludeGenres = form.excludeGenres
  if (mine('runtimeMax')) filters.runtimeMax = Number(form.runtimeMax)
  return filters
}

// Copia para os campos da tela o que a IA entendeu na busca
export function applyAiCriteria(form: SearchForm, criteria: AppliedCriteria, meta: Meta): SearchForm {
  const fromAi = (key: string) => criteria.sources?.[key] === 'ai'
  // O que a IA tinha preenchido antes some; ela decidiu de novo agora
  const next: SearchForm = { ...form, aiFields: [] }
  for (const field of form.aiFields) {
    if (field === 'genres' || field === 'excludeGenres') next[field] = []
    else next[field] = ''
  }

  if (fromAi('mediaType') && criteria.mediaType) {
    next.mediaType = criteria.mediaType
    next.aiFields.push('mediaType')
  }
  // A IA pode mandar ids de série (Sci-Fi & Fantasy): viram os da lista única
  const known = new Set(allGenres(meta).map((g) => g.id))
  if (fromAi('genres') && criteria.genres) {
    // Os gêneros da IA são sempre "qualquer um"
    const genres = canonicalGenres(criteria.genres).filter((g) => known.has(g))
    if (genres.length > 0) {
      next.genres = genres
      next.genreMatch = 'any'
      next.aiFields.push('genres')
    }
  }
  if (fromAi('excludeGenres') && criteria.excludeGenres) {
    const excluded = canonicalGenres(criteria.excludeGenres).filter((g) => known.has(g) && !next.genres.includes(g))
    if (excluded.length > 0) {
      next.excludeGenres = excluded
      next.aiFields.push('excludeGenres')
    }
  }
  if (fromAi('runtimeMax') && criteria.runtimeMax) {
    next.runtimeMax = String(criteria.runtimeMax)
    next.aiFields.push('runtimeMax')
  }
  return next
}
