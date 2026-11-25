import { RotateCcw, SlidersHorizontal, X } from 'lucide-react'
import { FilterPanel } from './FilterPanel'
import { clearFilters, type SearchForm } from '../lib/searchForm'
import { useModalDialog } from '../lib/useModalDialog'
import type { Meta } from '../types'

interface Props {
  open: boolean
  meta: Meta
  form: SearchForm
  loading: boolean
  onClose: () => void
  onChange: (form: SearchForm) => void
  onSubmit: () => void
}

// No celular a coluna de filtros não cabe: o mesmo painel abre numa janela de tela cheia
export function FiltersDialog({ open, meta, form, loading, onClose, onChange, onSubmit }: Props) {
  const dialogRef = useModalDialog(open)

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onClick={(e) => e.target === dialogRef.current && onClose()}
      className="m-auto w-[min(32rem,calc(100%-2rem))] max-w-none overflow-hidden rounded-2xl border border-line bg-surface p-0 text-ink backdrop:bg-black/70 backdrop:backdrop-blur-sm open:animate-pop-in max-sm:h-dvh max-sm:max-h-none max-sm:w-full max-sm:rounded-none max-sm:border-0"
    >
      {open && (
        <div className="flex max-h-[85vh] flex-col max-sm:h-full max-sm:max-h-none">
          <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-4">
            <h2 className="flex items-center gap-2.5 text-lg font-semibold">
              <SlidersHorizontal className="size-5 text-brand-soft" />
              Filtros
            </h2>
            <button
              type="button"
              onClick={onClose}
              aria-label="Fechar"
              className="rounded-lg p-1.5 text-muted transition hover:bg-surface-2 hover:text-ink"
            >
              <X className="size-5" />
            </button>
          </div>

          <div className="flex-1 overflow-y-auto px-5 py-5">
            <FilterPanel meta={meta} form={form} onChange={onChange} />
          </div>

          <div className="flex items-center justify-between gap-3 border-t border-line px-5 py-4">
            <button
              type="button"
              onClick={() => onChange(clearFilters(form))}
              className="flex items-center gap-2 rounded-xl px-3 py-2 text-sm text-muted transition hover:text-ink"
            >
              <RotateCcw className="size-4" /> Limpar filtros
            </button>
            <button
              type="button"
              onClick={onSubmit}
              disabled={loading || form.providers.length === 0}
              className="rounded-xl bg-brand px-5 py-2.5 font-semibold text-page transition hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-50"
            >
              Ver resultados
            </button>
          </div>
        </div>
      )}
    </dialog>
  )
}
