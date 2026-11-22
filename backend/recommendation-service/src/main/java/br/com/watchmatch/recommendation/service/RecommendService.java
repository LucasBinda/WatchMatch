package br.com.watchmatch.recommendation.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import br.com.watchmatch.recommendation.client.CatalogClient;
import br.com.watchmatch.recommendation.client.LlmClient;
import br.com.watchmatch.recommendation.model.AiMode;
import br.com.watchmatch.recommendation.model.AppliedCriteria;
import br.com.watchmatch.recommendation.model.Catalog.DiscoverRequest;
import br.com.watchmatch.recommendation.model.Catalog.Meta;
import br.com.watchmatch.recommendation.model.Catalog.TitleDetail;
import br.com.watchmatch.recommendation.model.Catalog.TitleList;
import br.com.watchmatch.recommendation.model.Catalog.TitleRef;
import br.com.watchmatch.recommendation.model.Catalog.TitleSummary;
import br.com.watchmatch.recommendation.model.Criteria;
import br.com.watchmatch.recommendation.model.Genre;
import br.com.watchmatch.recommendation.model.Llm.Candidate;
import br.com.watchmatch.recommendation.model.Llm.IntentRequest;
import br.com.watchmatch.recommendation.model.Llm.IntentResponse;
import br.com.watchmatch.recommendation.model.Llm.Pick;
import br.com.watchmatch.recommendation.model.Llm.RerankRequest;
import br.com.watchmatch.recommendation.model.Llm.RerankResponse;
import br.com.watchmatch.recommendation.model.Recommendation;
import br.com.watchmatch.recommendation.model.RecommendRequest;
import br.com.watchmatch.recommendation.model.RecommendResponse;
import br.com.watchmatch.recommendation.model.WatchInfo;
import br.com.watchmatch.recommendation.service.AiGuard.AiUnavailableException;

// O fluxo de docs/ARQUITETURA.md seção 4: texto → IA entende → mescla com filtros →
// catálogo busca candidatos reais → IA escolhe e justifica (modo "pick") → grounding → detalhes.
// No modo "filters" a IA só faz o primeiro passo e a ordem é a do catálogo.
@Service
public class RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendService.class);

    private static final int MAX_PAGE = 10;
    // Cada chamada ao catálogo traz uma página do TMDB por tipo: até 20 de cada
    private static final int CATALOG_LIMIT = 40;
    private static final int TMDB_PAGE_SIZE = 20;
    // A IA escolhe entre a página pedida e mais uma folga, até 60 candidatos (máximo do /rerank)
    private static final int RERANK_MIN_CANDIDATES = 30;
    private static final int RERANK_EXTRA = 12;
    private static final int RERANK_MAX_CANDIDATES = 60;
    // Total de caracteres de sinopse enviados à IA, dividido entre os candidatos.
    // Mantém o pedido dentro do limite de tokens por minuto do Groq gratuito (8k) até com 48 títulos.
    private static final int OVERVIEW_BUDGET = 9000;
    static final int OVERVIEW_MAX = 300;

    static final String OFF_TOPIC = "Não entendi isso como um pedido de filme ou série; mostrando títulos populares.";
    static final String RELAXED = "Não achei nada com tudo o que entendi do pedido; mostrando resultados só com os seus filtros.";
    static final String NOTHING_FOUND = "Nenhum título encontrado nas suas plataformas com esses critérios.";
    // Quando a IA fica de fora: um aviso por motivo (o frontend mostra no lugar do aviso genérico)
    static final String INTENT_FAILED = "A IA não respondeu desta vez: a busca usou só os filtros da tela.";
    static final String INTENT_PAUSED = "A IA está fora do ar por alguns instantes: a busca usou só os filtros da tela.";
    static final String PICK_FAILED = "A IA não conseguiu escolher desta vez: os títulos vieram na ordem do catálogo, sem justificativa.";
    static final String PICK_PAUSED = "A IA está fora do ar por alguns instantes: os títulos vieram na ordem do catálogo, sem justificativa.";
    static final String PICK_BUSY = "Muita gente buscando agora: a IA entendeu o pedido, mas os títulos vieram na ordem do catálogo, sem justificativa.";

    private final CatalogClient catalog;
    private final LlmClient llm;
    private final TextSanitizer sanitizer;
    private final CriteriaMerger merger;
    private final AiGuard guard;

    public RecommendService(CatalogClient catalog, LlmClient llm, TextSanitizer sanitizer, CriteriaMerger merger,
            AiGuard guard) {
        this.catalog = catalog;
        this.llm = llm;
        this.sanitizer = sanitizer;
        this.merger = merger;
        this.guard = guard;
    }

    public RecommendResponse recommend(RecommendRequest request, String requestId) {
        int limit = request.limitOrDefault();
        int page = request.pageOrDefault();
        AiMode mode = request.aiModeOrDefault();
        int needed = mode == AiMode.PICK ? rerankPool(limit) : limit;
        String text = sanitizer.clean(request.query());
        List<String> notices = new ArrayList<>();
        boolean degraded = false;

        // 1. A IA transforma o texto em critérios
        IntentResponse intent = null;
        if (text != null) {
            try {
                IntentRequest intentRequest = intentRequest(text);
                intent = guard.intent(() -> llm.intent(intentRequest));
            } catch (AiUnavailableException e) {
                log.info("/intent não chamado: circuito aberto");
                notices.add(INTENT_PAUSED);
                degraded = true;
            } catch (RestClientException e) {
                log.warn("llm-service /intent falhou: {}", e.getMessage());
                notices.add(INTENT_FAILED);
                degraded = true;
            }
            if (intent != null && Boolean.TRUE.equals(intent.offTopic())) {
                notices.add(OFF_TOPIC);
                intent = null;
            }
        }
        String summary = intent != null ? intent.summary() : null;

        // 2. Mescla com os filtros da tela e busca candidatos reais no catálogo
        MergedCriteria merged = merger.merge(request.filters(), intent, request.providers());
        TitleList found = discover(merged, page, needed);
        if (isEmpty(found) && intent != null) {
            // A IA pode ter entendido critérios demais: tenta só com o que o usuário marcou
            merged = merger.merge(request.filters(), null, request.providers());
            found = discover(merged, page, needed);
            notices.add(RELAXED);
            summary = null;
        }
        List<TitleSummary> candidates = isEmpty(found) ? List.of() : found.items();
        if (candidates.isEmpty()) {
            notices.add(NOTHING_FOUND);
        }

        // 3. Quem escolhe os títulos da página depende do modo
        List<Pick> picks = List.of();
        if (mode == AiMode.PICK && intent != null && !candidates.isEmpty()) {
            try {
                picks = pickWithAi(text, intent, candidates, limit);
            } catch (AiUnavailableException e) {
                boolean busy = e.reason() == AiGuard.Reason.BUSY;
                log.info("/rerank não chamado: {}", busy ? "limite suave" : "circuito aberto");
                notices.add(busy ? PICK_BUSY : PICK_PAUSED);
                degraded = true;
            } catch (RestClientException e) {
                log.warn("llm-service /rerank falhou: {}", e.getMessage());
                notices.add(PICK_FAILED);
                degraded = true;
            }
        }
        // Completa com a ordem do catálogo: no modo "filters" é a página inteira; no "pick", só o que a IA
        // deixou faltar. Assim a página vem cheia e a grade não fica com uma linha pela metade.
        picks = fillWithCatalogOrder(picks, candidates, limit);

        // 4. Detalhes (duração, classificação, onde assistir) só dos escolhidos
        List<Recommendation> items = details(picks, request.providers());

        var criteria = new AppliedCriteria(merged.criteria(), merged.providers(),
                intent != null ? intent.referenceTitle() : null, summary, merged.sources());
        boolean hasMore = found != null && Boolean.TRUE.equals(found.hasMore()) && page < MAX_PAGE;
        return new RecommendResponse(requestId, page, hasMore, criteria, items, degraded, notices);
    }

    private IntentRequest intentRequest(String text) {
        Meta meta = catalog.meta();
        // Gêneros de filme e de série juntos, sem repetir id
        Map<Integer, Genre> genres = new LinkedHashMap<>();
        Stream.concat(meta.genres().movie().stream(), meta.genres().tv().stream())
                .forEach(g -> genres.putIfAbsent(g.id(), g));
        return new IntentRequest(text, List.copyOf(genres.values()), meta.providers());
    }

    // Quantos candidatos a IA recebe para escolher `limit` títulos: 12 → 30, 24 → 36, 36 → 48, 48 → 60
    static int rerankPool(int limit) {
        return Math.min(Math.max(RERANK_MIN_CANDIDATES, limit + RERANK_EXTRA), RERANK_MAX_CANDIDATES);
    }

    // Uma página nossa = `perPage` páginas seguidas do TMDB. O número só depende de `needed`
    // (fixo enquanto o usuário clica em "Carregar mais"), então a página 2 começa onde a 1 acabou.
    // Conta como se viesse um tipo só (20 por página): com filme e série juntos sobra candidato.
    private TitleList discover(MergedCriteria merged, int page, int needed) {
        Criteria criteria = merged.criteria() != null ? merged.criteria() : Criteria.empty();
        int perPage = Math.ceilDiv(needed, TMDB_PAGE_SIZE);
        int first = (page - 1) * perPage + 1;

        Map<String, TitleSummary> items = new LinkedHashMap<>();
        boolean hasMore = false;
        for (int tmdbPage = first; tmdbPage < first + perPage; tmdbPage++) {
            TitleList list = catalog.discover(new DiscoverRequest(criteria, merged.providers(), CATALOG_LIMIT,
                    tmdbPage));
            if (list == null || list.items() == null) {
                break;
            }
            // O mesmo título pode aparecer em duas páginas do TMDB se a popularidade mudar no meio
            list.items().forEach(t -> items.putIfAbsent(key(t.mediaType(), t.id()), t));
            hasMore = Boolean.TRUE.equals(list.hasMore());
            if (!hasMore) {
                break;
            }
        }
        return new TitleList(List.copyOf(items.values()), hasMore);
    }

    // Modo "pick": a IA escolhe e justifica os títulos da página entre os candidatos reais
    private List<Pick> pickWithAi(String text, IntentResponse intent, List<TitleSummary> candidates, int limit) {
        List<Candidate> pool = toCandidates(candidates, rerankPool(limit));
        var rerankRequest = new RerankRequest(text, intent.summary(), pool, limit);
        RerankResponse rerank = guard.rerank(limit, () -> llm.rerank(rerankRequest));
        return ground(rerank.picks(), candidates, limit);
    }

    // Modo "filters" (ou sem IA): os primeiros do catálogo, depois de quem a IA já escolheu
    private List<Pick> fillWithCatalogOrder(List<Pick> picks, List<TitleSummary> candidates, int limit) {
        Set<String> used = picks.stream().map(p -> key(p.mediaType(), p.id())).collect(Collectors.toSet());
        Stream<Pick> rest = candidates.stream()
                .filter(c -> !used.contains(key(c.mediaType(), c.id())))
                .map(c -> new Pick(c.id(), c.mediaType(), null));
        return Stream.concat(picks.stream(), rest).limit(limit).toList();
    }

    private boolean isEmpty(TitleList list) {
        return list == null || list.items() == null || list.items().isEmpty();
    }

    // Mais candidatos = sinopses mais curtas: 30 → 300 caracteres, 60 → 150
    private List<Candidate> toCandidates(List<TitleSummary> titles, int pool) {
        int overviewMax = Math.min(OVERVIEW_MAX, OVERVIEW_BUDGET / pool);
        return titles.stream()
                .limit(pool)
                .map(t -> new Candidate(t.id(), t.mediaType(), t.title(), t.year(), shorten(t.overview(), overviewMax),
                        t.genres() == null ? List.of() : t.genres().stream().map(Genre::name).toList()))
                .toList();
    }

    private String shorten(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    // Grounding: só vale o que veio do catálogo. Id inventado ou repetido é descartado.
    private List<Pick> ground(List<Pick> picks, List<TitleSummary> candidates, int limit) {
        if (picks == null) {
            return List.of();
        }
        Set<String> real = candidates.stream().map(c -> key(c.mediaType(), c.id())).collect(Collectors.toSet());
        Set<String> used = new LinkedHashSet<>();
        List<Pick> grounded = picks.stream()
                .filter(p -> real.contains(key(p.mediaType(), p.id())))
                .filter(p -> used.add(key(p.mediaType(), p.id())))
                .limit(limit)
                .toList();
        int discarded = picks.size() - grounded.size();
        if (discarded > 0) {
            log.info("grounding descartou {} escolha(s) da IA fora dos candidatos", discarded);
        }
        return grounded;
    }

    private List<Recommendation> details(List<Pick> picks, List<Integer> userProviders) {
        List<TitleRef> refs = picks.stream().map(p -> new TitleRef(p.mediaType(), p.id())).toList();
        Map<String, TitleDetail> byKey = catalog.lookup(refs).stream()
                .collect(Collectors.toMap(d -> key(d.mediaType(), d.id()), Function.identity(), (a, b) -> a));

        return picks.stream()
                .filter(p -> byKey.containsKey(key(p.mediaType(), p.id())))
                .map(p -> toRecommendation(byKey.get(key(p.mediaType(), p.id())), p.reason(), userProviders))
                .toList();
    }

    private Recommendation toRecommendation(TitleDetail d, String reason, List<Integer> userProviders) {
        // Mostra só as plataformas que o usuário assina
        WatchInfo watch = d.watch() == null
                ? new WatchInfo(null, List.of())
                : new WatchInfo(d.watch().link(), d.watch().providers() == null ? List.of()
                        : d.watch().providers().stream().filter(p -> userProviders.contains(p.id())).toList());
        return new Recommendation(d.id(), d.mediaType(), d.title(), d.originalTitle(), d.year(), d.overview(),
                d.posterUrl(), d.backdropUrl(), d.genres(), d.voteAverage(), d.runtime(), d.seasons(),
                d.certification(), watch, reason);
    }

    private static String key(String mediaType, Integer id) {
        return mediaType + "-" + id;
    }
}
