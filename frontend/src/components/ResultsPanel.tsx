import { ChevronDown, CircleAlert, Info, LoaderCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { CriteriaChips } from './CriteriaChips'
import { TitleCard } from './TitleCard'
import { criteriaChips } from '../lib/format'
import { PAGE_SIZES } from '../lib/searchForm'
import type { Meta, PageSize, Recommendation, RecommendResponse } from '../types'

const EXAMPLES = [
  'Comédia leve para ver em casal, até 2 horas',
  'Animação para assistir com as crianças',
  'Suspense policial para maratonar',
]

interface Props {
  meta: Meta
  result: RecommendResponse | null
  loading: boolean
  loadingMore: boolean
  error: string | null
  pageSize: PageSize
  onPageSize: (size: PageSize) => void
  onLoadMore: () => void
  onExample: (query: string) => void
  onOpen: (title: Recommendation) => void
}

export function ResultsPanel({
  meta,
  result,
  loading,
  loadingMore,
  error,
  pageSize,
  onPageSize,
  onLoadMore,
  onExample,
  onOpen,
}: Props) {
  const count = result?.items.length ?? 0

  return (
    <section aria-live="polite">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex items-baseline gap-3">
          <h2 className="text-xl font-semibold sm:text-2xl">Sugestões</h2>
          <p className="text-sm text-muted">
            {loading
              ? 'Procurando títulos…'
              : result
                ? `${count} ${count === 1 ? 'título' : 'títulos'} nas suas plataformas`
                : null}
          </p>
        </div>
        <PageSizePicker value={pageSize} disabled={loading || loadingMore} onChange={onPageSize} />
      </div>

      {result && !loading && (
        <div className="mb-5 space-y-3">
          <CriteriaChips chips={criteriaChips(result.criteria, meta)} summary={result.criteria.summary} />
          {/* O backend explica por que a IA ficou de fora nos notices; o aviso genérico é só a reserva */}
          {result.degraded && result.notices.length === 0 && (
            <Notice icon={CircleAlert}>A IA está indisponível agora; mostrando títulos do catálogo sem justificativa.</Notice>
          )}
          {result.notices.map((n) => (
            <Notice key={n} icon={Info}>
              {n}
            </Notice>
          ))}
          {/* O backend já avisa quando não acha nada; este é a reserva, com uma dica quando o filtro de
              gêneros exigia todos de uma vez */}
          {count === 0 && result.notices.length === 0 && (
            <Notice icon={Info}>Nenhum conteúdo encontrado com esses filtros.</Notice>
          )}
          {count === 0 && result.criteria.genreMatch === 'all' && (result.criteria.genres?.length ?? 0) > 1 && (
            <Notice icon={Info}>
              Poucos títulos têm todos esses gêneros juntos. Tente "Qualquer um" no campo Gênero ou desmarque algum.
            </Notice>
          )}
        </div>
      )}

      {error && !loading && <Notice icon={CircleAlert}>{error}</Notice>}

      {loading ? (
        <div className="grid grid-cols-2 gap-4 sm:gap-5 md:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="animate-pulse overflow-hidden rounded-2xl border border-line">
              <div className="aspect-[2/3] bg-surface-2" />
              <div className="space-y-2 p-4">
                <div className="h-5 w-2/3 rounded bg-surface-2" />
                <div className="h-4 w-1/2 rounded bg-surface-2" />
                <div className="h-4 w-full rounded bg-surface-2" />
              </div>
            </div>
          ))}
        </div>
      ) : count > 0 ? (
        <>
          <div className="grid grid-cols-2 gap-4 sm:gap-5 md:grid-cols-3 lg:grid-cols-4">
            {result!.items.map((item) => (
              <TitleCard key={`${item.mediaType}-${item.id}`} title={item} onOpen={() => onOpen(item)} />
            ))}
          </div>
          {result!.hasMore ? (
            <div className="mt-6 flex justify-center">
              <button
                type="button"
                onClick={onLoadMore}
                disabled={loadingMore}
                className="flex items-center gap-2 rounded-xl border border-line bg-surface-2 px-5 py-2.5 font-medium transition hover:border-brand hover:text-brand-soft disabled:cursor-wait disabled:opacity-70"
              >
                {loadingMore ? (
                  <>
                    <LoaderCircle className="size-5 animate-spin" /> Carregando…
                  </>
                ) : (
                  <>
                    Carregar mais <ChevronDown className="size-5" />
                  </>
                )}
              </button>
            </div>
          ) : (
            <p className="mt-6 text-center text-sm text-faint">Esses são todos os títulos que combinam com o pedido.</p>
          )}
        </>
      ) : !result && !error ? (
        <div className="rounded-xl border border-dashed border-line px-5 py-10 text-center">
          <p className="text-muted">Sem ideia? Experimente um destes pedidos:</p>
          <div className="mt-4 flex flex-wrap justify-center gap-2">
            {EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => onExample(example)}
                className="rounded-full border border-line bg-surface-2 px-4 py-2 text-sm transition hover:border-brand hover:text-brand-soft"
              >
                {example}
              </button>
            ))}
          </div>
        </div>
      ) : null}
    </section>
  )
}

// "Mostrar 12 · 24 · 36 · 48": só múltiplos de 12, que fecham as linhas de 2, 3 ou 4 colunas
function PageSizePicker({
  value,
  disabled,
  onChange,
}: {
  value: PageSize
  disabled: boolean
  onChange: (size: PageSize) => void
}) {
  return (
    <div role="group" aria-label="Títulos por página" className="flex items-center gap-2 text-sm text-muted">
      <span>Mostrar</span>
      <div className="flex rounded-xl border border-line bg-surface-2 p-0.5">
        {PAGE_SIZES.map((size) => (
          <button
            key={size}
            type="button"
            aria-pressed={size === value}
            disabled={disabled}
            onClick={() => size !== value && onChange(size)}
            className={`min-w-10 rounded-lg px-2.5 py-1 font-medium tabular-nums transition disabled:cursor-wait ${
              size === value ? 'bg-brand text-page' : 'hover:text-ink'
            }`}
          >
            {size}
          </button>
        ))}
      </div>
    </div>
  )
}

function Notice({ icon: Icon, children }: { icon: typeof Info; children: ReactNode }) {
  return (
    <p className="flex items-start gap-2 rounded-xl border border-line bg-surface-2/70 px-3 py-2 text-sm text-muted">
      <Icon className="mt-0.5 size-4 shrink-0" />
      <span>{children}</span>
    </p>
  )
}
