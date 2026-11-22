package br.com.watchmatch.catalog.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.watchmatch.catalog.tmdb.TmdbClient;
import br.com.watchmatch.catalog.tmdb.TmdbKeywordPage;
import br.com.watchmatch.catalog.tmdb.TmdbKeywordPage.TmdbKeyword;

// O TMDB só filtra por id de palavra-chave: "heist" → 10051. Palavra sem correspondência é ignorada.
@Service
public class KeywordResolver {

    private static final int CACHE_SIZE = 2000;

    private final TmdbClient client;
    private final LruCache<String, Integer> cache = new LruCache<>(CACHE_SIZE);

    public KeywordResolver(TmdbClient client) {
        this.client = client;
    }

    public List<Integer> ids(List<String> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        return words.stream()
                .filter(w -> w != null && !w.isBlank())
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .map(w -> cache.get(w, this::lookup))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    // Prefere a palavra com o nome exato; senão, a primeira que o TMDB devolve
    private Optional<Integer> lookup(String word) {
        TmdbKeywordPage page = client.searchKeyword(word);
        if (page == null || page.results() == null || page.results().isEmpty()) {
            return Optional.empty();
        }
        return page.results().stream()
                .filter(k -> word.equalsIgnoreCase(k.name()))
                .findFirst()
                .or(() -> page.results().stream().findFirst())
                .map(TmdbKeyword::id)
                .filter(Objects::nonNull);
    }
}
