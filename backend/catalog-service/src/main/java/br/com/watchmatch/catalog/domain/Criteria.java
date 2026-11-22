package br.com.watchmatch.catalog.domain;

import java.util.List;

// Campos Integer/String podem ser null = "sem restrição".
// originalLanguage: ISO 639-1 ("ko"); keywords: temas em inglês ("heist"), viram ids do TMDB na busca
public record Criteria(
        List<Integer> genres,
        List<Integer> excludeGenres,
        Integer runtimeMin,
        Integer runtimeMax,
        Integer yearFrom,
        Integer yearTo,
        String originalLanguage,
        String certificationMax,
        List<String> keywords) {

    // Roda sempre que uma Criteria é criada: lista null vira lista vazia
    public Criteria {
        genres = genres == null ? List.of() : genres;
        excludeGenres = excludeGenres == null ? List.of() : excludeGenres;
        keywords = keywords == null ? List.of() : keywords;
    }

    public static Criteria empty() {
        return new Criteria(List.of(), List.of(), null, null, null, null, null, null, List.of());
    }

    // Cada with... devolve uma cópia da ficha com um único espaço trocado.
    // Records não podem ser alterados depois de criados, por isso é sempre uma ficha nova.

    public Criteria withGenres(List<Integer> genres) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withExcludeGenres(List<Integer> excludeGenres) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withRuntimeMin(Integer runtimeMin) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withRuntimeMax(Integer runtimeMax) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withYearFrom(Integer yearFrom) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withYearTo(Integer yearTo) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withOriginalLanguage(String originalLanguage) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withCertificationMax(String certificationMax) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }

    public Criteria withKeywords(List<String> keywords) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords);
    }
}
