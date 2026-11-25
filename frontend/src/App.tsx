import { LoaderCircle } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { getMeta, recommend } from './api/client'
import { FilterChips } from './components/FilterChips'
import { Footer } from './components/Footer'
import { Header } from './components/Header'
import { MockBanner } from './components/MockBanner'
import { PaletteSwitcher } from './components/PaletteSwitcher'
import { ResultsPanel } from './components/ResultsPanel'
import { FilterPanel } from './components/FilterPanel'
import { FiltersDialog } from './components/FiltersDialog'
import { SearchBox } from './components/SearchBox'
import { TitleDetails } from './components/TitleDetails'
import { paletteToolEnabled } from './lib/palettes'
import { loadAiMode, loadPageSize, loadProviders, saveAiMode, savePageSize, saveProviders } from './lib/preferences'
import { applyAiCriteria, filterChips, searchKey, userFilters, type SearchForm } from './lib/searchForm'
import type { Meta, PageSize, Recommendation, RecommendRequest, RecommendResponse } from './types'

export default function App() {
  const [meta, setMeta] = useState<Meta | null>(null)
  const [form, setForm] = useState<SearchForm>({
    query: '',
    genres: [],
    genreMatch: 'all',
    excludeGenres: [],
    runtimeMin: '',
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
  // O formulário como ficou depois da última busca (já com o que a IA entendeu). Diferente = "Buscar de novo"
  const [searchedKey, setSearchedKey] = useState<string | null>(null)
  const snapshotNext = useRef(false)

  useEffect(() => {
    getMeta()
      .then(setMeta)
      .catch(() => setError('Não foi possível carregar gêneros e plataformas.'))
  }, [])

  useEffect(() => saveProviders(form.providers), [form.providers])
  useEffect(() => saveAiMode(form.aiMode), [form.aiMode])
  // Roda depois que a resposta preencheu o formulário: guarda como ele ficou
  useEffect(() => {
    if (!snapshotNext.current) return
    snapshotNext.current = false
    setSearchedKey(searchKey(form))
  }, [form])

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
      snapshotNext.current = true
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

  const dirty = searchKey(form) !== searchedKey

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

      <main id="descobrir" className="px-4 pt-8 pb-14 sm:px-6 sm:pt-12 lg:px-8">
        <div className="mb-8 max-w-3xl">
          <h1 className="font-display text-3xl leading-[1.1] font-bold tracking-tight sm:text-4xl lg:text-5xl">
            O que vamos assistir <span className="text-brand">hoje?</span>
          </h1>
          <p className="mt-3 text-lg text-muted">
            Escreva do seu jeito e veja o que combina, nas plataformas que você já assina.
          </p>
        </div>

        {meta ? (
          <div className="md:grid md:grid-cols-[14rem_minmax(0,1fr)] md:gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-10 xl:grid-cols-[17rem_minmax(0,1fr)]">
            {/* Coluna de filtros, como nos marketplaces. Aparece a partir de 768 px (cobre zoom alto num
                monitor comum); abaixo disso vira a janela do botão "Filtros" */}
            <aside aria-label="Filtros" className="hidden md:block">
              <div className="sticky top-20 max-h-[calc(100dvh-6rem)] overflow-y-auto overscroll-contain pr-3 pb-4 [scrollbar-color:var(--color-line)_transparent] [scrollbar-width:thin]">
                <FilterPanel meta={meta} form={form} onChange={setForm} />
                {dirty && searchedKey !== null && (
                  <button
                    type="button"
                    onClick={submitSearch}
                    disabled={loading || form.providers.length === 0}
                    className="mt-5 w-full rounded-xl bg-brand py-2.5 font-semibold text-page transition hover:bg-brand-strong disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    Buscar com estes filtros
                  </button>
                )}
              </div>
            </aside>

            <div className="min-w-0 space-y-5">
              <SearchBox
                form={form}
                loading={loading}
                activeFilters={filterChips(form, meta).length}
                onChange={setForm}
                onSubmit={submitSearch}
                onOpenFilters={() => setSearchOpen(true)}
              />
              <FiltersDialog
                open={searchOpen}
                meta={meta}
                form={form}
                loading={loading}
                onClose={() => setSearchOpen(false)}
                onChange={setForm}
                onSubmit={submitSearch}
              />
              <FilterChips
                meta={meta}
                form={form}
                dirty={dirty}
                searched={searchedKey !== null}
                loading={loading}
                onChange={setForm}
                onSearch={submitSearch}
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
          </div>
        ) : error ? (
          <p className="text-star">{error}</p>
        ) : (
          <p className="flex items-center gap-2 text-muted">
            <LoaderCircle className="size-5 animate-spin" /> Carregando…
          </p>
        )}
      </main>

      <Footer />
      <TitleDetails title={selected} onClose={() => setSelected(null)} />
      {paletteToolEnabled() && <PaletteSwitcher />}
    </div>
  )
}
