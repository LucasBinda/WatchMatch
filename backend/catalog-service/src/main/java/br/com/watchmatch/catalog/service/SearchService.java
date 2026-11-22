package br.com.watchmatch.catalog.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.watchmatch.catalog.domain.TitleSummary;
import br.com.watchmatch.catalog.domain.TitleType;
import br.com.watchmatch.catalog.tmdb.TmdbClient;
import br.com.watchmatch.catalog.tmdb.TmdbMultiPage;
import br.com.watchmatch.catalog.tmdb.TmdbMultiPage.TmdbMultiResult;

// Busca por nome, em filmes e séries, na ordem de relevância do TMDB. Não filtra por plataforma.
@Service
public class SearchService {

    private final TmdbClient client;
    private final DiscoverService discover;

    public SearchService(TmdbClient client, DiscoverService discover) {
        this.client = client;
        this.discover = discover;
    }

    // mediaType null = filmes e séries
    public List<TitleSummary> search(String query, TitleType mediaType, int limit) {
        TmdbMultiPage page = client.searchMulti(query);
        if (page == null || page.results() == null) {
            return List.of();
        }
        return page.results().stream()
                .flatMap(r -> typeOf(r).filter(t -> mediaType == null || t == mediaType).stream()
                        .map(t -> discover.toSummary(r.toResult(), t)))
                .limit(limit)
                .toList();
    }

    // Pessoas (media_type "person") ficam de fora
    private static Optional<TitleType> typeOf(TmdbMultiResult result) {
        return switch (result.mediaType() == null ? "" : result.mediaType()) {
            case "movie" -> Optional.of(TitleType.MOVIE);
            case "tv" -> Optional.of(TitleType.TV);
            default -> Optional.empty();
        };
    }
}
