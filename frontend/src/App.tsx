import { LoaderCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getMeta, recommend } from './api/client'
import { Footer } from './components/Footer'
import { Header } from './components/Header'
import { MockBanner } from './components/MockBanner'
import { ResultsPanel } from './components/ResultsPanel'
import { SearchModal } from './components/SearchModal'
import { TitleDetails } from './components/TitleDetails'
import { loadAiMode, loadPageSize, loadProviders, saveAiMode, savePageSize, saveProviders } from './lib/preferences'
import { applyAiCriteria, userFilters, type SearchForm } from './lib/searchForm'
import type { Meta, PageSize, Recommendation, RecommendRequest, RecommendResponse } from './types'

export default function App() {
  const [meta, setMeta] = useState<Meta | null>(null)
  const [form, setForm] = useState<SearchForm>({
    query: '',
    genres: [],
    genreMatch: 'all',
    runtimeMax: '',
    mediaType: '',
    providers: loadProviders(),
    aiMode: loadAiMode(),
    aiFields: [],
  })
  const [pageSize, setPageSize] = useState<PageSize>(loadPageSize)
  const [result, setResult] = useState<RecommendResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  // "Carregar mais" repete este pedido com a próxima página
  const [lastRequest, setLastRequest] = useState<RecommendRequest | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Recommendation | null>(null)
  const [searchOpen, setSearchOpen] = useState(false)

  useEffect(() => {
    getMeta()
      .then(setMeta)
      .catch(() => setError('Não foi possível carregar gêneros e plataformas.'))
  }, [])

  useEffect(() => saveProviders(form.providers), [form.providers])
  useEffect(() => saveAiMode(form.aiMode), [form.aiMode])

  function search(current: SearchForm) {
    return run({
      query: current.query.trim() || undefined,
      filters: userFilters(current),
      providers: current.providers,
      limit: pageSize,
      aiMode: current.aiMode,
    })
  }

  async function run(request: RecommendRequest) {
    setLoading(true)
    setError(null)
    try {
      const response = await recommend(request)
      setResult(response)
      setLastRequest(request)
      // O que a IA entendeu aparece nos campos de Gênero, Duração e Formato
      if (meta) setForm((prev) => applyAiCriteria(prev, response.criteria, meta))
    } catch (e) {
      setResult(null)
      setError(e instanceof Error ? e.message : 'Algo deu errado. Tente de novo.')
    } finally {
      setLoading(false)
    }
  }

  async function loadMore() {
    if (!result || !lastRequest) return
    setLoadingMore(true)
    setError(null)
    try {
      const next = await recommend({ ...lastRequest, page: result.page + 1 })
      // A IA pode repetir um título entre páginas; mantém só a primeira aparição
      const seen = new Set(result.items.map((t) => `${t.mediaType}-${t.id}`))
      const fresh = next.items.filter((t) => !seen.has(`${t.mediaType}-${t.id}`))
      setResult({ ...result, items: [...result.items, ...fresh], page: next.page, hasMore: next.hasMore })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível carregar mais títulos.')
    } finally {
      setLoadingMore(false)
    }
  }

  // Trocar o tamanho refaz a última busca do começo, como nas lojas e streamings
  function changePageSize(size: PageSize) {
    setPageSize(size)
    savePageSize(size)
    if (lastRequest) run({ ...lastRequest, limit: size, page: undefined })
  }

  function submitSearch() {
    setSearchOpen(false)
    search(form)
  }

  function searchExample(query: string) {
    const next = { ...form, query }
    setForm(next)
    search(next)
  }

  return (
    <div className="min-h-dvh">
      <MockBanner />
      <Header />

      <main id="descobrir" className="mx-auto max-w-7xl px-4 py-10 sm:px-6 sm:py-14">
        <div className="space-y-8">
          <div>
            <h1 className="max-w-3xl text-3xl leading-tight font-bold tracking-tight sm:text-4xl lg:text-5xl">
              Filmes e séries para hoje, nas plataformas que você já assina
            </h1>
            <p className="mt-3 max-w-2xl text-lg text-muted">
              Escreva do seu jeito, como “comédia curta para ver em casal”, e veja onde assistir.
            </p>
          </div>

          {meta ? (
            <div className="space-y-6">
              <SearchModal
                open={searchOpen}
                meta={meta}
                form={form}
                loading={loading}
                onOpen={() => setSearchOpen(true)}
                onClose={() => setSearchOpen(false)}
                onChange={setForm}
                onSubmit={submitSearch}
              />
              <ResultsPanel
                meta={meta}
                result={result}
                loading={loading}
                loadingMore={loadingMore}
                error={error}
                pageSize={pageSize}
                onPageSize={changePageSize}
                onLoadMore={loadMore}
                onExample={searchExample}
                onOpen={setSelected}
              />
            </div>
          ) : error ? (
            <p className="text-star">{error}</p>
          ) : (
            <p className="flex items-center gap-2 text-muted">
              <LoaderCircle className="size-5 animate-spin" /> Carregando…
            </p>
          )}
        </div>
      </main>

      <Footer />
      <TitleDetails title={selected} onClose={() => setSelected(null)} />
    </div>
  )
}
