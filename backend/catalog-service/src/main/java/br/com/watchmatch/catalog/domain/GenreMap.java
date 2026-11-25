package br.com.watchmatch.catalog.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Filme e série têm listas de gênero diferentes no TMDB. O site usa uma lista só, com os ids de filme
// (e os de série para os gêneros que só existem em série); aqui cada id vira o do tipo buscado:
//   - iguais nos dois: Animação 16, Comédia 35, Crime 80, Documentário 99, Drama 18, Família 10751,
//     Mistério 9648, Faroeste 37;
//   - juntos em série: Ação 28 e Aventura 12 → Action & Adventure 10759; Ficção científica 878 e
//     Fantasia 14 → Sci-Fi & Fantasy 10765; Guerra 10752 → War & Politics 10768;
//   - só em filme, mas em série viram palavra-chave do TMDB: Terror 27 → "horror", Romance 10749 →
//     "romance", Thriller 53 → "thriller", História 36 → "history", Música 10402 → "musical".
//     Sem isso, "série de terror" não achava nenhuma série (e o afrouxamento mostrava as populares);
//   - só em filme, sem substituto: Cinema TV 10770;
//   - só em série: Kids 10762 (em filme vira Família 10751), News 10763, Reality 10764, Soap 10766, Talk 10767.
// Os ids de série também são aceitos (a IA recebe as duas listas e pode escolher 10765)
public final class GenreMap {

    private static final Map<Integer, Integer> MOVIE_TO_TV = Map.of(
            28, 10759, 12, 10759,
            878, 10765, 14, 10765,
            10752, 10768);
    private static final Map<Integer, List<Integer>> TV_TO_MOVIE = Map.of(
            10759, List.of(28, 12),
            10765, List.of(878, 14),
            10768, List.of(10752),
            10762, List.of(10751));
    private static final Set<Integer> MOVIE_ONLY = Set.of(36, 27, 10402, 10749, 53, 10770);
    private static final Set<Integer> TV_ONLY = Set.of(10763, 10764, 10766, 10767);
    // Em série, o gênero de filme vira a palavra-chave do TMDB (em inglês, como as da IA)
    private static final Map<Integer, String> TV_KEYWORD = Map.of(
            27, "horror",
            10749, "romance",
            53, "thriller",
            36, "history",
            10402, "musical");

    private GenreMap() {
    }

    // Critérios do tipo buscado. keywords: palavras que substituem gêneros que o tipo não tem (só em série);
    // allKeywords: o título precisa ter todas ("todos os gêneros"); senão, qualquer uma.
    // excludeKeywords: as dos gêneros proibidos
    public record Typed(Criteria criteria, List<String> keywords, boolean allKeywords, List<String> excludeKeywords) {

        public boolean hasGenreKeywords() {
            return !keywords.isEmpty();
        }
    }

    // O id no tipo pedido. Lista vazia = o gênero não existe nesse tipo
    public static List<Integer> toType(int id, TitleType type) {
        if (type == TitleType.TV) {
            if (MOVIE_TO_TV.containsKey(id)) {
                return List.of(MOVIE_TO_TV.get(id));
            }
            return MOVIE_ONLY.contains(id) ? List.of() : List.of(id);
        }
        if (TV_TO_MOVIE.containsKey(id)) {
            return TV_TO_MOVIE.get(id);
        }
        return TV_ONLY.contains(id) ? List.of() : List.of(id);
    }

    // Palavra-chave que faz as vezes do gênero no tipo pedido, se houver
    private static Optional<String> keywordFor(int id, TitleType type) {
        return type == TitleType.TV ? Optional.ofNullable(TV_KEYWORD.get(id)) : Optional.empty();
    }

    // Vazio = impossível nesse tipo: "Cinema TV" em séries, ou "todos" com um gênero que o tipo não tem
    // nem por palavra-chave. Buscar mesmo assim traria qualquer título, sem o gênero
    public static Optional<Typed> forType(Criteria criteria, TitleType type) {
        List<Integer> genres;
        List<String> keywords = new ArrayList<>();
        if (criteria.allGenres()) {
            Set<Integer> each = new LinkedHashSet<>();
            for (int id : criteria.genres()) {
                List<Integer> mapped = toType(id, type);
                if (!mapped.isEmpty()) {
                    // Gênero de série que vira dois de filme (Action & Adventure): basta o primeiro
                    each.add(mapped.get(0));
                    continue;
                }
                Optional<String> keyword = keywordFor(id, type);
                if (keyword.isEmpty()) {
                    return Optional.empty();
                }
                keywords.add(keyword.get());
            }
            genres = List.copyOf(each);
        } else {
            genres = mapAll(criteria.genres(), type);
            // "Comédia ou Terror" em série: o TMDB não junta gênero e palavra-chave num "ou"; vale o gênero
            // que existe. Só quando nenhum existe é que as palavras-chave entram
            if (!criteria.genres().isEmpty() && genres.isEmpty()) {
                criteria.genres().forEach(id -> keywordFor(id, type).ifPresent(keywords::add));
                if (keywords.isEmpty()) {
                    return Optional.empty();
                }
            }
        }
        // Proibir o que o tipo não tem: por palavra-chave, se houver; senão não muda nada.
        // E o que foi pedido não fica proibido (pediu Ação e proibiu Aventura: em série as duas são
        // Action & Adventure, e vale o pedido)
        List<Integer> wanted = genres;
        List<Integer> excluded = mapAll(criteria.excludeGenres(), type).stream()
                .filter(g -> !wanted.contains(g))
                .toList();
        List<String> excludeKeywords = criteria.excludeGenres().stream()
                .filter(id -> toType(id, type).isEmpty())
                .map(id -> keywordFor(id, type))
                .flatMap(Optional::stream)
                .filter(k -> !keywords.contains(k))
                .distinct()
                .toList();
        return Optional.of(new Typed(criteria.withGenres(genres).withExcludeGenres(excluded),
                List.copyOf(new LinkedHashSet<>(keywords)), criteria.allGenres(), excludeKeywords));
    }

    private static List<Integer> mapAll(List<Integer> ids, TitleType type) {
        Set<Integer> out = new LinkedHashSet<>();
        for (int id : ids) {
            out.addAll(toType(id, type));
        }
        return new ArrayList<>(out);
    }
}
