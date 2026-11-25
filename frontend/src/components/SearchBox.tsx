import { ArrowRight, LoaderCircle, Search, SlidersHorizontal } from 'lucide-react'
import type { FormEvent, KeyboardEvent } from 'react'
import type { SearchForm } from '../lib/searchForm'

const MAX_QUERY = 500

interface Props {
  form: SearchForm
  loading: boolean
  activeFilters: number
  onChange: (form: SearchForm) => void
  onSubmit: () => void
  onOpenFilters: () => void
}

// O pedido em texto livre, no topo dos resultados. Os filtros ficam na coluna da esquerda
// (em tela estreita, na barra "Filtros" logo abaixo)
export function SearchBox({ form, loading, activeFilters, onChange, onSubmit, onOpenFilters }: Props) {
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit()
  }

  // Enter envia, Shift+Enter quebra linha
  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      e.currentTarget.form?.requestSubmit()
    }
  }

  const noProvider = form.providers.length === 0

  return (
    <form onSubmit={handleSubmit} role="search">
      <div className="flex items-end gap-2 rounded-2xl border border-line bg-surface p-2 transition focus-within:border-brand focus-within:ring-2 focus-within:ring-brand/25">
        <Search className="mb-3 ml-2 size-5 shrink-0 text-brand-soft" aria-hidden="true" />
        <label htmlFor="query" className="sr-only">
          O que você quer assistir?
        </label>
        <textarea
          id="query"
          rows={1}
          maxLength={MAX_QUERY}
          value={form.query}
          onChange={(e) => onChange({ ...form, query: e.target.value })}
          onKeyDown={handleKeyDown}
          placeholder="Ex.: uma aventura de ficção científica para ver com amigos, com menos de 2 horas"
          className="max-h-40 min-h-11 flex-1 resize-none bg-transparent py-2.5 text-base leading-relaxed outline-none [field-sizing:content] placeholder:text-faint"
        />
        <button
          type="submit"
          disabled={loading || noProvider}
          title={noProvider ? 'Escolha pelo menos uma plataforma' : undefined}
          className="flex h-11 items-center gap-2 rounded-xl bg-brand px-4 font-semibold text-page transition hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-50 sm:px-5"
        >
          {loading ? <LoaderCircle className="size-5 animate-spin" /> : <ArrowRight className="size-5 sm:hidden" />}
          <span className="max-sm:sr-only">{loading ? 'Procurando…' : 'Buscar'}</span>
        </button>
      </div>
      {/* Sem a coluna de filtros (tela estreita ou zoom alto), os filtros abrem por esta barra */}
      <button
        type="button"
        onClick={onOpenFilters}
        className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl border border-line bg-surface px-4 py-2.5 text-sm font-medium text-ink transition hover:border-brand md:hidden"
      >
        <SlidersHorizontal className="size-4 text-brand-soft" />
        Filtros
        {activeFilters > 0 && (
          <span className="grid size-5 place-items-center rounded-full bg-brand text-[11px] font-bold text-page">
            {activeFilters}
          </span>
        )}
      </button>
      {form.query.length > MAX_QUERY * 0.8 && (
        <p className="mt-1 text-right text-xs text-muted">
          {form.query.length}/{MAX_QUERY}
        </p>
      )}
    </form>
  )
}
