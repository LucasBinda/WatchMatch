package br.com.watchmatch.catalog.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.watchmatch.catalog.domain.Criteria;
import br.com.watchmatch.catalog.domain.DiscoverRequest;
import br.com.watchmatch.catalog.domain.Reference;
import br.com.watchmatch.catalog.domain.SimilarRequest;
import br.com.watchmatch.catalog.domain.SimilarResult;
import br.com.watchmatch.catalog.domain.SortBy;
import br.com.watchmatch.catalog.domain.TitleDetail;
import br.com.watchmatch.catalog.domain.TitleList;
import br.com.watchmatch.catalog.domain.TitleRef;
import br.com.watchmatch.catalog.domain.TitleSummary;
import br.com.watchmatch.catalog.domain.TitleType;
import br.com.watchmatch.catalog.tmdb.TmdbClient;
import br.com.watchmatch.catalog.tmdb.TmdbPage;
import br.com.watchmatch.catalog.tmdb.TmdbReference;

// "Parecido com X", no modelo híbrido (docs/ARQUITETURA.md):
//  1. acha X pelo nome;
//  2. primeiro, as recomendações do TMDB para X que estão nas plataformas do usuário e batem com os critérios
//     (o TMDB não filtra recomendação por plataforma: confere uma a uma, em paralelo);
//  3. completa com uma busca normal pelos gêneros e palavras-chave de X, que já respeita as plataformas.
@Service
public class SimilarService {

    private static final int CACHE_SIZE = 500;
    // Mais que isso dilui a semelhança; o TMDB lista as palavras mais marcantes primeiro
    private static final int MAX_REFERENCE_KEYWORDS = 8;
    private static final int FILL_LIMIT = 40;
    // As mesmas ofertas do /discover: assinatura, grátis e grátis com anúncios (sem aluguel e compra)
    private static final Set<String> INCLUDED_OFFERS = Set.of("flatrate", "free", "ads");
    private static final List<String> CERTIFICATIONS = List.of("L", "10", "12", "14", "16", "18");

    private final TmdbClient client;
    private final SearchService search;
    private final DiscoverService discover;
    private final TitleService titles;
    // O usuário clica em "Carregar mais": a mesma referência é pedida de novo a cada página
    private final LruCache<String, Found> references = new LruCache<>(CACHE_SIZE);

    public SimilarService(TmdbClient client, SearchService search, DiscoverService discover, TitleService titles) {
        this.client = client;
        this.search = search;
        this.discover = discover;
        this.titles = titles;
    }

    private record Found(TitleType type, TmdbReference info) {
    }

    public SimilarResult similar(SimilarRequest request, int limit) {
        Optional<Found> found = references.get(cacheKey(request), key -> find(request));
        if (found.isEmpty()) {
            return new SimilarResult(null, List.of(), false);
        }
        TitleType refType = found.get().type();
        TmdbReference ref = found.get().info();
        Criteria criteria = request.criteria() != null ? request.criteria() : Criteria.empty();
        int page = request.page() != null ? request.page() : 1;
        // Sem tipo pedido, "parecido com um filme" traz filmes
        TitleType targetType = request.mediaType() != null ? request.mediaType() : refType;

        Map<String, TitleSummary> items = new LinkedHashMap<>();
        boolean hasMore = false;

        if (targetType == refType) {
            TmdbPage recommended = client.recommendations(refType, ref.id(), page);
            recommendedAvailable(recommended, refType, request.providers(), criteria)
                    .forEach(t -> items.putIfAbsent(key(t.mediaType(), t.id()), t));
            hasMore = recommended != null && DiscoverService.hasNextPage(recommended);
        }

        // Filme e série têm ids de gênero diferentes no TMDB (ficção científica: 878 e 10765): com tipos
        // diferentes ("série parecida com Interestelar") a semelhança vem só das palavras-chave, que são as mesmas
        boolean useRefGenres = criteria.genres().isEmpty() && targetType == refType;
        // Os gêneros de X valem como "qualquer um": exigir todos deixaria quase só o próprio X
        Criteria fillCriteria = useRefGenres ? criteria.withGenres(ref.genreIds()).withGenreMatch(null) : criteria;
        List<Integer> refKeywords = ref.keywordIds().stream().limit(MAX_REFERENCE_KEYWORDS).toList();
        TitleList fill = discover.discover(
                new DiscoverRequest(fillCriteria, request.providers(), SortBy.POPULARITY, targetType, page),
                FILL_LIMIT, refKeywords);
        fill.items().forEach(t -> items.putIfAbsent(key(t.mediaType(), t.id()), t));
        hasMore |= Boolean.TRUE.equals(fill.hasMore());

        // O próprio X nunca é "parecido com X"
        items.remove(key(refType, ref.id()));

        List<TitleSummary> list = new ArrayList<>(items.values());
        return new SimilarResult(new Reference(ref.id(), refType, ref.displayTitle()),
                list.subList(0, Math.min(limit, list.size())), hasMore);
    }

    // O primeiro resultado da busca por nome é o mais relevante para o TMDB. Busca em filmes e séries:
    // o tipo pedido vale para os resultados, não para X ("série parecida com Interestelar")
    private Optional<Found> find(SimilarRequest request) {
        return search.search(request.title(), null, 1).stream()
                .findFirst()
                .map(hit -> new Found(hit.mediaType(), client.reference(hit.mediaType(), hit.id())));
    }

    private List<TitleSummary> recommendedAvailable(TmdbPage page, TitleType type, List<Integer> providers,
            Criteria criteria) {
        if (page == null || page.results() == null || page.results().isEmpty()) {
            return List.of();
        }
        List<TitleRef> refs = page.results().stream().map(r -> new TitleRef(type, r.id())).toList();
        // Na ordem do TMDB, que é a ordem de semelhança
        return titles.lookup(refs).stream()
                .filter(d -> onUserPlatforms(d, providers) && matches(d, criteria))
                .map(SimilarService::toSummary)
                .toList();
    }

    static boolean onUserPlatforms(TitleDetail detail, List<Integer> providers) {
        return detail.watch() != null && detail.watch().providers() != null
                && detail.watch().providers().stream()
                        .anyMatch(p -> providers.contains(p.id()) && INCLUDED_OFFERS.contains(p.offer()));
    }

    // Os mesmos critérios que o /discover aplica; informação que o TMDB não tem não elimina o título
    static boolean matches(TitleDetail d, Criteria c) {
        if (!DiscoverService.runtimeFits(d.runtime(), c)) {
            return false;
        }
        if (d.year() != null && c.yearFrom() != null && d.year() < c.yearFrom()) {
            return false;
        }
        if (d.year() != null && c.yearTo() != null && d.year() > c.yearTo()) {
            return false;
        }
        if (c.originalLanguage() != null && d.originalLanguage() != null
                && !c.originalLanguage().equalsIgnoreCase(d.originalLanguage())) {
            return false;
        }
        List<Integer> genreIds = d.genres() == null ? List.of() : d.genres().stream().map(g -> g.id()).toList();
        if (genreIds.stream().anyMatch(c.excludeGenres()::contains)) {
            return false;
        }
        if (!c.genres().isEmpty() && (c.allGenres() ? !genreIds.containsAll(c.genres())
                : genreIds.stream().noneMatch(c.genres()::contains))) {
            return false;
        }
        return certificationAllowed(d.certification(), c.certificationMax());
    }

    private static boolean certificationAllowed(String certification, String max) {
        if (certification == null || max == null) {
            return true;
        }
        int rank = CERTIFICATIONS.indexOf(certification);
        int maxRank = CERTIFICATIONS.indexOf(max);
        return rank < 0 || maxRank < 0 || rank <= maxRank;
    }

    private static TitleSummary toSummary(TitleDetail d) {
        return new TitleSummary(d.id(), d.mediaType(), d.title(), d.originalTitle(), d.year(), d.overview(),
                d.posterUrl(), d.genres(), d.voteAverage(), d.originalLanguage());
    }

    private static String cacheKey(SimilarRequest request) {
        return request.title().trim().toLowerCase(Locale.ROOT);
    }

    private static String key(TitleType type, Integer id) {
        return type + "-" + id;
    }
}
