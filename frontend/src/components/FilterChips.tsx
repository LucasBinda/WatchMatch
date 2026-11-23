import { Fragment } from 'react'
import { RefreshCw, Sparkles, X } from 'lucide-react'
import { clearFilters, filterChips, removeFilter, type FilterChip, type SearchForm } from '../lib/searchForm'
import type { Meta } from '../types'

interface Props {
  meta: Meta
  form: SearchForm
  // O formulário mudou desde a última busca (ou ainda não houve busca)
  dirty: boolean
  searched: boolean
  loading: boolean
  onChange: (form: SearchForm) => void
  onSearch: () => void
}

// Os filtros escolhidos, abaixo da barra de busca. O X tira do formulário; a busca só roda no botão
export function FilterChips({ meta, form, dirty, searched, loading, onChange, onSearch }: Props) {
  const chips = filterChips(form, meta)
  const showSearch = dirty && (searched || chips.length > 0)
  if (chips.length === 0 && !showSearch) return null

  const genreCount = chips.filter((c) => c.kind === 'genre').length
  const joiner = form.genreMatch === 'all' ? '+' : 'ou'
  let genresSeen = 0

  return (
    <div className="flex flex-wrap items-center gap-2 text-sm" aria-label="Filtros escolhidos">
      {chips.map((chip) => {
        // Entre gêneros pedidos, "+" (todos) ou "ou" (pelo menos um) deixa claro como se combinam
        const showJoiner = chip.kind === 'genre' && genresSeen++ > 0 && genreCount > 1
        return (
          <Fragment key={chipKey(chip)}>
            {showJoiner && <span className="text-xs text-faint">{joiner}</span>}
            <Chip chip={chip} onRemove={() => onChange(removeFilter(form, chip, meta))} />
          </Fragment>
        )
      })}
      {chips.length > 1 && (
        <button
          type="button"
          onClick={() => onChange(clearFilters(form))}
          className="rounded-full px-2 py-1 text-muted underline-offset-4 transition hover:text-ink hover:underline"
        >
          Limpar
        </button>
      )}
      {showSearch && (
        <button
          type="button"
          onClick={onSearch}
          disabled={loading || form.providers.length === 0}
          className="ml-auto flex items-center gap-1.5 rounded-full bg-brand px-3.5 py-1.5 font-medium text-page transition hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-50"
        >
          <RefreshCw className={`size-3.5 ${loading ? 'animate-spin' : ''}`} />
          {searched ? 'Buscar de novo' : 'Buscar'}
        </button>
      )}
    </div>
  )
}

function Chip({ chip, onRemove }: { chip: FilterChip; onRemove: () => void }) {
  const banned = chip.kind === 'excludeGenre'
  const style = banned
    ? 'border-danger/40 bg-danger/10 text-danger'
    : chip.ai
      ? 'border-brand/50 bg-brand/15 text-brand-soft'
      : 'border-line bg-surface-2 text-ink/85'
  return (
    <span
      title={chip.ai ? 'Preenchido pela IA' : 'Filtro escolhido por você'}
      className={`flex items-center gap-1 rounded-full border py-1 pr-1 pl-3 ${style}`}
    >
      {chip.ai && <Sparkles className="size-3.5" />}
      {chip.label}
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Tirar ${chip.label}`}
        className="rounded-full p-0.5 opacity-70 transition hover:bg-black/20 hover:opacity-100"
      >
        <X className="size-3.5" />
      </button>
    </span>
  )
}

function chipKey(chip: FilterChip): string {
  return 'id' in chip ? `${chip.kind}-${chip.id}` : chip.kind
}
