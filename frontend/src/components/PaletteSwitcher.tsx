import { ChevronLeft, ChevronRight, Copy, Palette as PaletteIcon, RotateCcw, Shuffle, Sparkles, X } from 'lucide-react'
import { useState } from 'react'
import {
  PALETTES,
  TOKENS,
  applyPalette,
  clearPalette,
  generatePalette,
  loadPalette,
  paletteToCss,
  savePalette,
  type Palette,
} from '../lib/palettes'

// Ferramenta de escolha de paleta: troca as cores do site na hora. Não aparece para o público
// (só em desenvolvimento ou com ?paletas na URL)
export function PaletteSwitcher() {
  const [open, setOpen] = useState(false)
  const [current, setCurrent] = useState<Palette | null>(loadPalette)
  const [copied, setCopied] = useState(false)

  const index = current ? PALETTES.findIndex((p) => p.name === current.name) : 0

  function choose(palette: Palette) {
    applyPalette(palette)
    savePalette(palette)
    setCurrent(palette)
    setCopied(false)
  }

  function step(delta: number) {
    const from = index < 0 ? 0 : index
    choose(PALETTES[(from + delta + PALETTES.length) % PALETTES.length])
  }

  function random() {
    const others = PALETTES.filter((p) => p.name !== current?.name)
    choose(others[Math.floor(Math.random() * others.length)])
  }

  function reset() {
    clearPalette()
    setCurrent(null)
  }

  async function copy() {
    if (!current) return
    try {
      await navigator.clipboard.writeText(paletteToCss(current))
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Abrir seletor de paletas"
        title="Trocar paleta de cores"
        className="fixed right-4 bottom-4 z-50 grid size-12 place-items-center rounded-full border border-line bg-surface text-brand shadow-xl shadow-black/30 transition hover:scale-105"
      >
        <PaletteIcon className="size-5" />
      </button>
    )
  }

  const shown = current ?? PALETTES[0]

  return (
    <div
      role="region"
      aria-label="Seletor de paletas"
      className="fixed right-4 bottom-4 z-50 w-[min(20rem,calc(100vw-2rem))] rounded-2xl border border-line bg-surface p-4 text-sm shadow-2xl shadow-black/40"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs text-muted">
            Paleta {index >= 0 ? `${index + 1} de ${PALETTES.length}` : 'gerada'}
          </p>
          <p className="truncate font-semibold">{shown.name}</p>
        </div>
        <button type="button" onClick={() => setOpen(false)} aria-label="Fechar seletor" className="rounded-md p-1 text-muted hover:text-ink">
          <X className="size-4" />
        </button>
      </div>

      <div className="mt-3 flex gap-1" aria-hidden="true">
        {TOKENS.map((t) => (
          <span key={t} title={t} className="h-6 flex-1 rounded border border-line" style={{ background: shown.colors[t] }} />
        ))}
      </div>

      <div className="mt-3 grid grid-cols-4 gap-1.5">
        <ToolButton label="Anterior" onClick={() => step(-1)} icon={ChevronLeft} />
        <ToolButton label="Próxima" onClick={() => step(1)} icon={ChevronRight} />
        <ToolButton label="Sortear" onClick={random} icon={Shuffle} />
        <ToolButton label="Gerar nova" onClick={() => choose(generatePalette())} icon={Sparkles} />
      </div>

      <div className="mt-2 flex gap-1.5">
        <button
          type="button"
          onClick={copy}
          disabled={!current}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-line px-2 py-1.5 text-xs text-muted transition hover:text-ink disabled:opacity-50"
        >
          <Copy className="size-3.5" /> {copied ? 'Copiado' : 'Copiar cores'}
        </button>
        <button
          type="button"
          onClick={reset}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-line px-2 py-1.5 text-xs text-muted transition hover:text-ink"
        >
          <RotateCcw className="size-3.5" /> Voltar ao padrão
        </button>
      </div>
    </div>
  )
}

function ToolButton({ label, onClick, icon: Icon }: { label: string; onClick: () => void; icon: typeof Shuffle }) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      className="flex flex-col items-center gap-1 rounded-lg bg-surface-2 px-1 py-2 text-[11px] text-muted transition hover:bg-brand/15 hover:text-ink"
    >
      <Icon className="size-4" />
      {label}
    </button>
  )
}
