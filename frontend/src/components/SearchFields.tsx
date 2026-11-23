import { ArrowRight, LoaderCircle } from 'lucide-react'
import type { KeyboardEvent } from 'react'
import { GenrePicker } from './GenrePicker'
import { PlatformPicker } from './PlatformPicker'
import { SelectField } from './SelectField'
import { AI_MODE_OPTIONS, MEDIA_OPTIONS, genresFor, runtimeOptions, setField, setGenreFilter, type SearchForm } from '../lib/searchForm'
import type { Meta } from '../types'

const MAX_QUERY = 500

interface Props {
  meta: Meta
  form: SearchForm
  onChange: (form: SearchForm) => void
  wide?: boolean
  autoFocus?: boolean
}

export function SearchFields({ meta, form, onChange, wide = false, autoFocus = false }: Props) {
  const set = <K extends keyof SearchForm>(key: K, value: SearchForm[K]) => onChange({ ...form, [key]: value })

  const genres = genresFor(form.mediaType, meta)
  const ai = (field: SearchForm['aiFields'][number]) => form.aiFields.includes(field)

  function toggleProvider(id: number) {
    const providers = form.providers.includes(id)
      ? form.providers.filter((p) => p !== id)
      : [...form.providers, id]
    set('providers', providers)
  }

  // Enter envia, Shift+Enter quebra linha
  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault()
      e.currentTarget.form?.requestSubmit()
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <label htmlFor="query" className="mb-2 block text-sm text-ink/90">
          O que você quer assistir? <span className="text-muted">(opcional)</span>
        </label>
        <textarea
          id="query"
          rows={3}
          maxLength={MAX_QUERY}
          autoFocus={autoFocus}
          data-autofocus={autoFocus || undefined}
          value={form.query}
          onChange={(e) => set('query', e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ex.: Quero uma aventura de ficção científica para ver com amigos, com menos de 2 horas"
          className="w-full resize-none rounded-xl border border-line bg-surface-2 px-4 py-3 text-[15px] leading-relaxed outline-none transition placeholder:text-faint focus:border-brand focus:ring-2 focus:ring-brand/30"
        />
        {form.query.length > MAX_QUERY * 0.8 && (
          <p className="mt-1 text-right text-xs text-muted">
            {form.query.length}/{MAX_QUERY}
          </p>
        )}
      </div>

      <div className={wide ? 'grid gap-4 sm:grid-cols-3' : 'space-y-5'}>
        <GenrePicker
          genres={genres}
          selected={form.genres}
          excluded={form.excludeGenres}
          match={form.genreMatch}
          ai={ai('genres') || ai('excludeGenres')}
          onChange={(change) => onChange(setGenreFilter(form, change))}
        />
        <SelectField
          id="runtime"
          label="Duração"
          value={form.runtimeMax}
          ai={ai('runtimeMax')}
          onChange={(v) => onChange(setField(form, 'runtimeMax', v))}
          options={runtimeOptions(form.runtimeMax)}
        />
        <SelectField
          id="mediaType"
          label="Formato"
          value={form.mediaType}
          ai={ai('mediaType')}
          onChange={(v) => onChange(setField(form, 'mediaType', v))}
          options={MEDIA_OPTIONS}
        />
      </div>

      <fieldset>
        <legend className="mb-2 text-sm text-ink/90">Como a IA ajuda</legend>
        <div className={`grid gap-2 ${wide ? 'sm:grid-cols-2' : ''}`}>
          {AI_MODE_OPTIONS.map((option) => {
            const isOn = form.aiMode === option.value
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={isOn}
                onClick={() => set('aiMode', option.value)}
                className={`rounded-xl border px-3 py-2 text-left transition ${
                  isOn
                    ? 'border-brand bg-brand/15 text-ink'
                    : 'border-line bg-surface-2 text-muted hover:border-faint hover:text-ink'
                }`}
              >
                <span className="block text-sm font-medium">{option.label}</span>
                <span className="block text-xs text-muted">{option.hint}</span>
              </button>
            )
          })}
        </div>
      </fieldset>

      <PlatformPicker
        providers={meta.providers}
        selected={form.providers}
        onToggle={toggleProvider}
        gridClassName={wide ? 'grid-cols-2 sm:grid-cols-4' : 'grid-cols-2'}
      />
    </div>
  )
}

export function SearchSubmit({ loading, disabled }: { loading: boolean; disabled: boolean }) {
  return (
    <button
      type="submit"
      disabled={loading || disabled}
      className="flex items-center justify-center gap-2 rounded-xl bg-brand px-5 py-3 font-semibold text-page transition hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading && <LoaderCircle className="size-5 animate-spin" />}
      {loading ? 'Procurando…' : 'Encontrar recomendações'}
      {!loading && <ArrowRight className="size-5" />}
    </button>
  )
}
