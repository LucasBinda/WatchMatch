import { RUNTIME_LIMIT, RUNTIME_SHORTCUTS, RUNTIME_STEP, runtimeRangeLabel } from '../lib/searchForm'

interface Props {
  min: string // minutos ou '' (sem mínimo)
  max: string // minutos ou '' (sem máximo)
  onChange: (min: string, max: string) => void
}

const TICKS = [0, 60, 120, 180, 240]

// Slider de dois pontos de 0 a 4h+. Ponto no começo = sem mínimo; no fim = sem máximo.
// São dois <input type="range"> sobrepostos: cada um tem seu rótulo e funciona com as setas do teclado
export function RuntimeSlider({ min, max, onChange }: Props) {
  // A IA pode pedir mais de 4h (ex.: 300): o ponto fica no fim da faixa
  const lo = min === '' ? 0 : Math.min(Number(min), RUNTIME_LIMIT)
  const hi = max === '' ? RUNTIME_LIMIT : Math.min(Number(max), RUNTIME_LIMIT)

  function moveMin(value: number) {
    const v = Math.min(value, hi)
    onChange(v <= 0 ? '' : String(v), max)
  }

  function moveMax(value: number) {
    // Máximo 0 não existe (o contrato pede pelo menos 1 minuto)
    const v = Math.max(value, lo, RUNTIME_STEP)
    onChange(min, v >= RUNTIME_LIMIT ? '' : String(v))
  }

  const pct = (v: number) => (v / RUNTIME_LIMIT) * 100
  const label = runtimeRangeLabel(min, max)

  return (
    <div>
      <p className="mb-3 text-sm text-ink" aria-live="polite">
        {label}
      </p>

      <div className="relative h-5">
        <div className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-line" />
        <div
          className="absolute top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-brand"
          style={{ left: `${pct(lo)}%`, right: `${100 - pct(hi)}%` }}
        />
        <input
          type="range"
          min={0}
          max={RUNTIME_LIMIT}
          step={RUNTIME_STEP}
          value={lo}
          onChange={(e) => moveMin(Number(e.target.value))}
          aria-label="Duração mínima"
          aria-valuetext={min ? runtimeRangeLabel(min, '') : 'Sem mínimo'}
          // Com os dois pontos encostados no fim, o do mínimo fica por cima para poder ser puxado de volta
          className={`range-thumbs absolute inset-0 w-full ${lo > RUNTIME_LIMIT / 2 ? 'z-20' : 'z-10'}`}
        />
        <input
          type="range"
          min={0}
          max={RUNTIME_LIMIT}
          step={RUNTIME_STEP}
          value={hi}
          onChange={(e) => moveMax(Number(e.target.value))}
          aria-label="Duração máxima"
          aria-valuetext={max ? runtimeRangeLabel('', max) : 'Sem máximo'}
          className="range-thumbs absolute inset-0 z-10 w-full"
        />
      </div>

      <div className="mt-1.5 flex justify-between text-[11px] text-faint" aria-hidden="true">
        {TICKS.map((t) => (
          <span key={t}>{t === 0 ? '0' : t === RUNTIME_LIMIT ? '4h+' : `${t / 60}h`}</span>
        ))}
      </div>

      <div className="mt-3 flex flex-wrap gap-1.5">
        {RUNTIME_SHORTCUTS.map((s) => {
          const on = s.min === min && s.max === max
          return (
            <button
              key={s.label}
              type="button"
              aria-pressed={on}
              onClick={() => (on ? onChange('', '') : onChange(s.min, s.max))}
              className={`rounded-full border px-2.5 py-1 text-xs transition ${
                on
                  ? 'border-brand bg-brand/15 text-ink'
                  : 'border-line text-muted hover:border-faint hover:text-ink'
              }`}
            >
              {s.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
