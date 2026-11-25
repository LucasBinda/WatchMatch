import { Check } from 'lucide-react'
import { shortProviderName } from '../lib/format'
import type { Provider } from '../types'

interface Props {
  providers: Provider[]
  selected: number[]
  onToggle: (id: number) => void
  // Na coluna de filtros: sem legenda (a seção já tem título) e botões menores
  compact?: boolean
}

export function PlatformPicker({ providers, selected, onToggle, compact = false }: Props) {
  const grid = (
    <div className="grid grid-cols-2 gap-1.5">
      {providers.map((p) => {
        const isOn = selected.includes(p.id)
        return (
          <button
            key={p.id}
            type="button"
            aria-pressed={isOn}
            onClick={() => onToggle(p.id)}
            className={`relative flex items-center gap-2 rounded-lg border text-left text-xs font-medium transition ${
              compact ? 'px-1.5 py-1.5' : 'px-2.5 py-2'
            } ${
              isOn
                ? 'border-brand bg-brand/15 text-ink'
                : 'border-line bg-surface-2 text-muted hover:border-faint hover:text-ink'
            }`}
          >
            {p.logoUrl && <img src={p.logoUrl} alt="" className={`shrink-0 rounded-md ${compact ? 'size-6' : 'size-7'}`} />}
            <span className="line-clamp-2">{shortProviderName(p.name)}</span>
            {isOn && <Check className="absolute -top-1.5 -right-1.5 size-4 rounded-full bg-brand p-0.5 text-page" />}
          </button>
        )
      })}
    </div>
  )

  const warning = selected.length === 0 && (
    <p className="mt-2 text-xs text-star">Escolha pelo menos uma plataforma.</p>
  )

  if (compact) {
    return (
      <div>
        {grid}
        {warning}
      </div>
    )
  }

  return (
    <fieldset>
      <legend className="mb-2 text-sm text-ink/90">Plataformas que você assina</legend>
      {grid}
      {warning}
    </fieldset>
  )
}
