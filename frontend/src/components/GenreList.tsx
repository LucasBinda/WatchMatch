import { Ban, Check } from 'lucide-react'
import { useId, useState } from 'react'
import { availableFor, unavailableHint, type UnifiedGenre } from '../lib/genres'
import type { GenreChange } from '../lib/searchForm'
import type { GenreMatch } from '../types'

interface Props {
  genres: UnifiedGenre[]
  mediaType: string
  selected: number[]
  excluded: number[]
  match: GenreMatch
  onChange: (change: GenreChange) => void
}

const MATCH_OPTIONS: { value: GenreMatch; label: string }[] = [
  { value: 'all', label: 'todos os gêneros' },
  { value: 'any', label: 'pelo menos um' },
]

// Quantos gêneros aparecem antes de "Mostrar todos", como nos filtros de marketplace
const FIRST = 8

// Lista de gêneros sempre aberta (coluna de filtros). Em cada linha: a caixa marca "quero";
// o botão ⊘ à direita marca "não quero"
export function GenreList({ genres, mediaType, selected, excluded, match, onChange }: Props) {
  const [expanded, setExpanded] = useState(false)
  const matchId = useId()

  // Marcados e proibidos ficam sempre visíveis, mesmo com a lista recolhida
  const visible = expanded ? genres : genres.filter((g, i) => i < FIRST || selected.includes(g.id) || excluded.includes(g.id))

  const unusable = genres
    .filter((g) => (selected.includes(g.id) || excluded.includes(g.id)) && !availableFor(g, mediaType))
    .map((g) => g.name)

  function toggleWanted(id: number) {
    onChange({ genres: selected.includes(id) ? selected.filter((g) => g !== id) : [...selected, id] })
  }

  function toggleBanned(id: number) {
    onChange({ excludeGenres: excluded.includes(id) ? excluded.filter((g) => g !== id) : [...excluded, id] })
  }

  return (
    <div>
      {selected.length > 1 && (
        <div className="mb-2 rounded-lg bg-surface-2 p-2">
          <p id={matchId} className="mb-1 px-1 text-xs text-muted">
            Mostrar títulos que tenham:
          </p>
          <div role="radiogroup" aria-labelledby={matchId} className="grid gap-0.5">
            {MATCH_OPTIONS.map((o) => (
              <button
                key={o.value}
                type="button"
                role="radio"
                aria-checked={match === o.value}
                onClick={() => onChange({ genreMatch: o.value })}
                className={`flex items-center gap-2.5 rounded-md px-1.5 py-1 text-left text-sm transition ${
                  match === o.value ? 'text-ink' : 'text-muted hover:text-ink'
                }`}
              >
                <RadioDot on={match === o.value} />
                {o.label}
              </button>
            ))}
          </div>
        </div>
      )}

      <ul className="-mx-2">
        {visible.map((g) => {
          const isOn = selected.includes(g.id)
          const isBanned = excluded.includes(g.id)
          // Gênero que o formato escolhido não tem (Terror em séries): continua marcável, mas apagado.
          // Se estiver marcado, volta a valer ao trocar o formato
          const hint = unavailableHint(g, mediaType)
          return (
            <li
              key={g.id}
              title={hint ?? undefined}
              className={`group flex items-center rounded-lg hover:bg-surface-2 ${hint ? 'opacity-45' : ''}`}
            >
              <label className="flex min-w-0 flex-1 cursor-pointer items-center gap-3 px-2 py-1.5 text-sm">
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
                <span className={`truncate ${isOn ? 'text-ink' : isBanned ? 'text-danger line-through' : 'text-muted'}`}>
                  {g.name}
                </span>
                {hint && <span className="sr-only">({hint})</span>}
              </label>
              <button
                type="button"
                aria-pressed={isBanned}
                aria-label={isBanned ? `Voltar a aceitar ${g.name}` : `Não quero ${g.name}`}
                title={isBanned ? 'Proibido. Clique para voltar a aceitar' : 'Não quero este gênero'}
                onClick={() => toggleBanned(g.id)}
                className={`mr-1 rounded-md p-1 transition ${
                  isBanned
                    ? 'bg-danger/15 text-danger'
                    : 'text-faint opacity-60 group-hover:opacity-100 hover:text-danger focus-visible:opacity-100'
                }`}
              >
                <Ban className="size-3.5" />
              </button>
            </li>
          )
        })}
      </ul>

      {unusable.length > 0 && (
        <p className="mt-2 rounded-lg bg-surface-2 px-2.5 py-1.5 text-xs text-muted">
          {unusable.join(', ')} não {unusable.length === 1 ? 'existe' : 'existem'} em{' '}
          {mediaType === 'tv' ? 'séries' : 'filmes'} no TMDB e {unusable.length === 1 ? 'fica' : 'ficam'} de fora
          nesta busca.
        </p>
      )}

      <div className="mt-1 flex items-center justify-between gap-2 text-sm">
        {genres.length > FIRST && (
          <button type="button" onClick={() => setExpanded((v) => !v)} className="text-brand-soft hover:underline">
            {expanded ? 'Mostrar menos' : `Mostrar todos (${genres.length})`}
          </button>
        )}
        {(selected.length > 0 || excluded.length > 0) && (
          <button type="button" onClick={() => onChange({ genres: [], excludeGenres: [] })} className="text-muted hover:text-ink">
            Limpar
          </button>
        )}
      </div>
    </div>
  )
}

export function RadioDot({ on }: { on: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={`block size-3.5 shrink-0 rounded-full border transition ${on ? 'border-4 border-brand' : 'border-faint'}`}
    />
  )
}
