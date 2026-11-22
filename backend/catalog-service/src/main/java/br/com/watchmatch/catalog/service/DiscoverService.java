package br.com.watchmatch.catalog.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import br.com.watchmatch.catalog.domain.DiscoverQueryBuilder;
import br.com.watchmatch.catalog.domain.DiscoverRequest;
import br.com.watchmatch.catalog.domain.TitleList;
import br.com.watchmatch.catalog.domain.TitleSummary;
import br.com.watchmatch.catalog.domain.TitleType;
import br.com.watchmatch.catalog.tmdb.TitleMapper;
import br.com.watchmatch.catalog.tmdb.TmdbClient;
import br.com.watchmatch.catalog.tmdb.TmdbPage;
import br.com.watchmatch.catalog.tmdb.TmdbResult;

@Service
public class DiscoverService {

    private final TmdbClient client;
    private final DiscoverQueryBuilder queryBuilder;
    private final TitleMapper mapper;
    private final KeywordResolver keywords;

    // A lista de gêneros quase nunca muda: busca uma vez por tipo e guarda
    private final Map<TitleType, Map<Integer, String>> genreNames = new ConcurrentHashMap<>();

    public DiscoverService(TmdbClient client, DiscoverQueryBuilder queryBuilder, TitleMapper mapper,
            KeywordResolver keywords) {
        this.client = client;
        this.queryBuilder = queryBuilder;
        this.mapper = mapper;
        this.keywords = keywords;
    }

    public TitleList discover(DiscoverRequest request, int limit) {
        return discover(request, limit, List.of());
    }

    // extraKeywordIds: ids que já vêm prontos (as palavras-chave do título de referência, no /similar)
    public TitleList discover(DiscoverRequest request, int limit, List<Integer> extraKeywordIds) {
        List<Integer> keywordIds = Stream.concat(keywords.ids(request.criteria().keywords()).stream(),
                extraKeywordIds.stream()).distinct().toList();
        // Sem mediaType, busca os dois tipos
        List<TitleType> types = request.mediaType() != null
                ? List.of(request.mediaType())
                : List.of(TitleType.MOVIE, TitleType.TV);

        List<List<TitleSummary>> perType = new ArrayList<>();
        boolean hasMore = false;

        for (TitleType type : types) {
            TmdbPage page = client.discover(type, queryBuilder.build(request, type, keywordIds));
            perType.add(toSummaries(page, type));
            hasMore |= hasNextPage(page);
        }

        List<TitleSummary> items = interleave(perType);
        return new TitleList(items.subList(0, Math.min(limit, items.size())), hasMore);
    }

    // Um resultado avulso do TMDB (ex.: da busca por nome), com os nomes dos gêneros em português
    TitleSummary toSummary(TmdbResult result, TitleType type) {
        return mapper.toSummary(result, type, genreNames.computeIfAbsent(type, client::genres));
    }

    List<TitleSummary> toSummaries(TmdbPage page, TitleType type) {
        if (page.results() == null) {
            return List.of();
        }
        Map<Integer, String> names = genreNames.computeIfAbsent(type, client::genres);
        return page.results().stream()
                .map(result -> mapper.toSummary(result, type, names))
                .toList();
    }

    static boolean hasNextPage(TmdbPage page) {
        return page.page() != null && page.totalPages() != null && page.page() < page.totalPages();
    }

    // [F1, F2, F3] + [S1] vira [F1, S1, F2, F3]
    private List<TitleSummary> interleave(List<List<TitleSummary>> lists) {
        List<TitleSummary> result = new ArrayList<>();
        int longest = lists.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < longest; i++) {
            for (List<TitleSummary> list : lists) {
                if (i < list.size()) {
                    result.add(list.get(i));
                }
            }
        }
        return result;
    }
}
