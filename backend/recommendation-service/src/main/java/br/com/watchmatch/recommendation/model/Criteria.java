package br.com.watchmatch.recommendation.model;

import java.util.List;

// Critérios de busca (common.yaml). Campo null ou lista vazia = "sem restrição".
// mediaType: "movie" ou "tv"; certificationMax: "L", "10", "12", "14", "16" ou "18"
// genreMatch: "all" = o título precisa ter todos os gêneros (só vem da tela); null = qualquer um deles
public record Criteria(
        String mediaType,
        List<Integer> genres,
        List<Integer> excludeGenres,
        Integer runtimeMin,
        Integer runtimeMax,
        Integer yearFrom,
        Integer yearTo,
        String originalLanguage,
        String certificationMax,
        List<String> keywords,
        String genreMatch) {

    // Sem genreMatch: a IA e a maior parte do código não usam esse campo
    public Criteria(String mediaType, List<Integer> genres, List<Integer> excludeGenres, Integer runtimeMin,
            Integer runtimeMax, Integer yearFrom, Integer yearTo, String originalLanguage, String certificationMax,
            List<String> keywords) {
        this(mediaType, genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, null);
    }

    public static Criteria empty() {
        return new Criteria(null, null, null, null, null, null, null, null, null, null);
    }

    // Para afrouxar a busca: sem as palavras-chave
    public Criteria withoutKeywords() {
        return new Criteria(mediaType, genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo,
                originalLanguage, certificationMax, null, genreMatch);
    }

    // Para afrouxar a busca: sem idioma original e sem duração mínima
    public Criteria withoutLanguageAndRuntimeMin() {
        return new Criteria(mediaType, genres, excludeGenres, null, runtimeMax, yearFrom, yearTo, null,
                certificationMax, keywords, genreMatch);
    }
}
