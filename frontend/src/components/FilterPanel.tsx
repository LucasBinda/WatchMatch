import { Sparkles } from 'lucide-react'
import type { ReactNode } from 'react'
import { GenreList, RadioDot } from './GenreList'
import { PlatformPicker } from './PlatformPicker'
import { allGenres } from '../lib/genres'
import {
  AI_MODE_OPTIONS,
  MEDIA_OPTIONS,
  runtimeOptions,
  setField,
  setGenreFilter,
  type SearchForm,
} from '../lib/searchForm'
import type { Meta } from '../types'

interface Props {
  meta: Meta
  form: SearchForm
  onChange: (form: SearchForm) => void
}

// Os filtros em seções, um embaixo do outro, como na coluna da esquerda dos marketplaces.
// Na tela larga fica fixo ao lado dos resultados; no celular aparece na janela de filtros
export function FilterPanel({ meta, form, onChange }: Props) {
  const ai = (field: SearchForm['aiFields'][number]) => form.aiFields.includes(field)

  function toggleProvider(id: number) {
    const providers = form.providers.includes(id)
      ? form.providers.filter((p) => p !== id)
      : [...form.providers, id]
    onChange({ ...form, providers })
  }

  return (
    <div className="divide-y divide-line">
      <Section title="Formato" ai={ai('mediaType')}>
        <OptionList
          name="Formato"
          value={form.mediaType}
          options={MEDIA_OPTIONS}
          onChange={(v) => onChange(setField(form, 'mediaType', v))}
        />
      </Section>

      <Section title="Gênero" ai={ai('genres') || ai('excludeGenres')}>
        <GenreList
          genres={allGenres(meta)}
          mediaType={form.mediaType}
          selected={form.genres}
          excluded={form.excludeGenres}
          match={form.genreMatch}
          onChange={(change) => onChange(setGenreFilter(form, change))}
        />
      </Section>

      <Section title="Duração" ai={ai('runtimeMax')}>
        <OptionList
          name="Duração"
          value={form.runtimeMax}
          options={runtimeOptions(form.runtimeMax)}
          onChange={(v) => onChange(setField(form, 'runtimeMax', v))}
        />
      </Section>

      <Section title="Plataformas que você assina">
        <PlatformPicker providers={meta.providers} selected={form.providers} onToggle={toggleProvider} compact />
      </Section>

      <Section title="Como a IA ajuda">
        <OptionList
          name="Como a IA ajuda"
          value={form.aiMode}
          options={AI_MODE_OPTIONS.map((o) => ({ value: o.value, label: o.label, hint: o.hint }))}
          onChange={(v) => onChange({ ...form, aiMode: v as SearchForm['aiMode'] })}
        />
      </Section>
    </div>
  )
}

function Section({ title, ai = false, children }: { title: string; ai?: boolean; children: ReactNode }) {
  return (
    <section className="py-4 first:pt-0 last:pb-0">
      <h3 className="mb-2.5 flex items-center gap-2 text-sm font-semibold text-ink">
        {title}
        {ai && (
          <span
            title="Preenchido pela IA. Mude para fixar como seu."
            className="flex items-center gap-1 rounded-full bg-brand/15 px-1.5 py-0.5 text-[11px] font-medium text-brand-soft"
          >
            <Sparkles className="size-3" /> IA
          </span>
        )}
      </h3>
      {children}
    </section>
  )
}

interface Option {
  value: string
  label: string
  hint?: string
}

function OptionList({
  name,
  value,
  options,
  onChange,
}: {
  name: string
  value: string
  options: Option[]
  onChange: (value: string) => void
}) {
  return (
    <div role="radiogroup" aria-label={name} className="-mx-2 grid gap-0.5">
      {options.map((o) => {
        const on = o.value === value
        return (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => onChange(o.value)}
            className={`flex items-start gap-2.5 rounded-lg px-2 py-1.5 text-left text-sm transition hover:bg-surface-2 ${
              on ? 'text-ink' : 'text-muted hover:text-ink'
            }`}
          >
            <span className="mt-[3px] shrink-0">
              <RadioDot on={on} />
            </span>
            <span>
              <span className="block">{o.label}</span>
              {o.hint && <span className="block text-xs text-faint">{o.hint}</span>}
            </span>
          </button>
        )
      })}
    </div>
  )
}
