import { Ban, Check, ChevronDown, Sparkles } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import type { GenreChange } from '../lib/searchForm'
import type { Genre, GenreMatch } from '../types'

interface Props {
  genres: Genre[]
  selected: number[]
  excluded: number[]
  match: GenreMatch
  ai?: boolean // algum gênero (pedido ou proibido) preenchido pela IA
  onChange: (change: GenreChange) => void
}

const MATCH_OPTIONS: { value: GenreMatch; label: string }[] = [
  { value: 'all', label: 'todos os gêneros' },
  { value: 'any', label: 'pelo menos um' },
]

// Campo do mesmo tamanho dos outros; clicando, abre a lista de gêneros.
// Em cada linha: a caixa marca "quero"; o botão ⊘ à direita marca "não quero"
export function GenrePicker({ genres, selected, excluded, match, ai = false, onChange }: Props) {
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

  const wanted = genres.filter((g) => selected.includes(g.id)).map((g) => g.name)
  const banned = genres.filter((g) => excluded.includes(g.id)).map((g) => g.name.toLowerCase())
  const summary = [
    wanted.length === 0 ? '' : wanted.length <= 2 ? wanted.join(match === 'all' ? ' + ' : ' ou ') : `${wanted.length} gêneros`,
    banned.length === 0 ? '' : banned.length === 1 ? `sem ${banned[0]}` : `sem ${banned.length}`,
  ]
    .filter(Boolean)
    .join(', ')

  function toggleWanted(id: number) {
    onChange({ genres: selected.includes(id) ? selected.filter((g) => g !== id) : [...selected, id] })
  }

  function toggleBanned(id: number) {
    onChange({ excludeGenres: excluded.includes(id) ? excluded.filter((g) => g !== id) : [...excluded, id] })
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
        <span className={`block truncate ${summary ? 'text-ink' : ''}`}>{summary || 'Qualquer gênero'}</span>
        <ChevronDown
          className={`pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted transition ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div
          id={listId}
          className="absolute right-0 left-0 z-30 mt-2 min-w-64 rounded-xl border border-line bg-surface shadow-xl shadow-black/40"
        >
          {selected.length > 1 && (
            <div className="border-b border-line p-3">
              <p id={`${listId}-match`} className="mb-2 text-xs text-muted">
                Mostrar títulos que tenham:
              </p>
              <div role="radiogroup" aria-labelledby={`${listId}-match`} className="grid gap-1">
                {MATCH_OPTIONS.map((o) => (
                  <button
                    key={o.value}
                    type="button"
                    role="radio"
                    aria-checked={match === o.value}
                    onClick={() => onChange({ genreMatch: o.value })}
                    className={`flex items-center gap-2.5 rounded-lg px-2 py-1.5 text-left text-sm transition ${
                      match === o.value ? 'bg-brand/20 text-ink' : 'text-muted hover:bg-surface-2 hover:text-ink'
                    }`}
                  >
                    <span
                      aria-hidden="true"
                      className={`size-3.5 shrink-0 rounded-full border ${
                        match === o.value ? 'border-4 border-brand' : 'border-faint'
                      }`}
                    />
                    {o.label}
                  </button>
                ))}
              </div>
            </div>
          )}
          <ul className="max-h-64 overflow-y-auto overscroll-contain p-1">
            {genres.map((g) => {
              const isOn = selected.includes(g.id)
              const isBanned = excluded.includes(g.id)
              return (
                <li key={g.id} className="flex items-center rounded-lg hover:bg-surface-2">
                  <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 px-3 py-2 text-sm">
                    <input
                      type="checkbox"
                      checked={isOn}
                      onChange={() => toggleWanted(g.id)}
                      aria-label={`Quero ${g.name}`}
                      className="peer sr-only"
                    />
                    <span
                      aria-hidden="true"
                      className={`flex size-4 shrink-0 items-center justify-center rounded border transition peer-focus-visible:ring-2 peer-focus-visible:ring-brand/40 ${
                        isOn ? 'border-brand bg-brand text-page' : 'border-faint'
                      }`}
                    >
                      {isOn && <Check className="size-3" strokeWidth={3} />}
                    </span>
                    <span
                      className={`truncate ${isOn ? 'text-ink' : isBanned ? 'text-danger line-through' : 'text-muted'}`}
                    >
                      {g.name}
                    </span>
                  </label>
                  <button
                    type="button"
                    aria-pressed={isBanned}
                    aria-label={isBanned ? `Voltar a aceitar ${g.name}` : `Não quero ${g.name}`}
                    title={isBanned ? 'Proibido. Clique para voltar a aceitar' : 'Não quero este gênero'}
                    onClick={() => toggleBanned(g.id)}
                    className={`mr-1 rounded-md p-1.5 transition ${
                      isBanned ? 'bg-danger/15 text-danger' : 'text-faint hover:bg-surface hover:text-danger'
                    }`}
                  >
                    <Ban className="size-4" />
                  </button>
                </li>
              )
            })}
          </ul>
          {(selected.length > 0 || excluded.length > 0) && (
            <div className="border-t border-line p-2">
              <button
                type="button"
                onClick={() => onChange({ genres: [], excludeGenres: [] })}
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
