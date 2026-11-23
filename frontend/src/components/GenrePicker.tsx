import { Check, ChevronDown, Sparkles } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import type { Genre, GenreMatch } from '../types'

interface Props {
  genres: Genre[]
  selected: number[]
  match: GenreMatch
  ai?: boolean // gêneros preenchidos pela IA
  onChange: (selected: number[], match: GenreMatch) => void
}

const MATCH_OPTIONS: { value: GenreMatch; label: string; hint: string }[] = [
  { value: 'all', label: 'Todos', hint: 'O título precisa ter todos os gêneros marcados' },
  { value: 'any', label: 'Qualquer um', hint: 'Basta o título ter um dos gêneros marcados' },
]

// Campo do mesmo tamanho dos outros; clicando, abre a lista de gêneros com caixas de marcar
export function GenrePicker({ genres, selected, match, ai = false, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const listId = useId()

  // Fecha ao clicar fora ou apertar Esc. Esc não fecha o modal de busca junto (o <dialog> fecha no Esc;
  // preventDefault no keydown cancela isso)
  useEffect(() => {
    if (!open) return
    function onPointer(e: PointerEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false)
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault()
        e.stopPropagation()
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey, true)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey, true)
    }
  }, [open])

  const names = genres.filter((g) => selected.includes(g.id)).map((g) => g.name)
  const summary =
    names.length === 0
      ? 'Qualquer gênero'
      : names.length <= 2
        ? names.join(match === 'all' ? ' + ' : ' ou ')
        : `${names.length} gêneros`

  function toggle(id: number) {
    onChange(selected.includes(id) ? selected.filter((g) => g !== id) : [...selected, id], match)
  }

  return (
    <div ref={rootRef} className="relative">
      <span id={`${listId}-label`} className="mb-2 flex items-center gap-2 text-sm text-ink/90">
        Gênero
        {ai && (
          <span
            title="Preenchido pela IA. Mude a seleção para fixar como sua."
            className="flex items-center gap-1 rounded-full border border-brand/50 bg-brand/15 px-2 py-0.5 text-xs text-brand-soft"
          >
            <Sparkles className="size-3" /> IA
          </span>
        )}
      </span>
      <button
        type="button"
        id="genre"
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={listId}
        aria-labelledby={`${listId}-label genre`}
        onClick={() => setOpen((v) => !v)}
        className={`relative w-full rounded-xl border ${ai ? 'border-brand/50' : 'border-line'} bg-surface-2 px-4 py-2.5 pr-10 text-left text-[15px] outline-none transition focus:border-brand focus:ring-2 focus:ring-brand/30`}
      >
        <span className={`block truncate ${names.length ? 'text-ink' : ''}`}>{summary}</span>
        <ChevronDown
          className={`pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted transition ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div
          id={listId}
          className="absolute right-0 left-0 z-30 mt-2 min-w-56 rounded-xl border border-line bg-surface shadow-xl shadow-black/40"
        >
          {selected.length > 1 && (
            <div role="radiogroup" aria-label="Combinar gêneros" className="grid grid-cols-2 gap-1 border-b border-line p-2">
              {MATCH_OPTIONS.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  role="radio"
                  aria-checked={match === o.value}
                  title={o.hint}
                  onClick={() => onChange(selected, o.value)}
                  className={`rounded-lg px-2 py-1.5 text-sm transition ${
                    match === o.value ? 'bg-brand/20 text-ink' : 'text-muted hover:bg-surface-2 hover:text-ink'
                  }`}
                >
                  {o.label}
                </button>
              ))}
            </div>
          )}
          <ul className="max-h-64 overflow-y-auto overscroll-contain p-1">
            {genres.map((g) => {
              const isOn = selected.includes(g.id)
              return (
                <li key={g.id}>
                  <label className="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-sm hover:bg-surface-2">
                    <input type="checkbox" checked={isOn} onChange={() => toggle(g.id)} className="peer sr-only" />
                    <span
                      aria-hidden="true"
                      className={`flex size-4 shrink-0 items-center justify-center rounded border transition peer-focus-visible:ring-2 peer-focus-visible:ring-brand/40 ${
                        isOn ? 'border-brand bg-brand text-page' : 'border-faint'
                      }`}
                    >
                      {isOn && <Check className="size-3" strokeWidth={3} />}
                    </span>
                    <span className={isOn ? 'text-ink' : 'text-muted'}>{g.name}</span>
                  </label>
                </li>
              )
            })}
          </ul>
          {selected.length > 0 && (
            <div className="border-t border-line p-2">
              <button
                type="button"
                onClick={() => onChange([], match)}
                className="w-full rounded-lg px-3 py-1.5 text-sm text-muted transition hover:bg-surface-2 hover:text-ink"
              >
                Limpar gêneros
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
