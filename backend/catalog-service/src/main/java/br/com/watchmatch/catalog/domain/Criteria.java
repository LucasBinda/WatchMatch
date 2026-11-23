package br.com.watchmatch.catalog.domain;

import java.util.List;

// Campos Integer/String podem ser null = "sem restrição".
// originalLanguage: ISO 639-1 ("ko"); keywords: temas em inglês ("heist"), viram ids do TMDB na busca
// genreMatch: "all" = o título precisa ter todos os gêneros; "any" ou null = qualquer um deles
public record Criteria(
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

    public static final String ALL_GENRES = "all";

    // Roda sempre que uma Criteria é criada: lista null vira lista vazia
    public Criteria {
        genres = genres == null ? List.of() : genres;
        excludeGenres = excludeGenres == null ? List.of() : excludeGenres;
        keywords = keywords == null ? List.of() : keywords;
    }

    public static Criteria empty() {
        return new Criteria(List.of(), List.of(), null, null, null, null, null, null, List.of(), null);
    }

    // Cada with... devolve uma cópia da ficha com um único espaço trocado.
    // Records não podem ser alterados depois de criados, por isso é sempre uma ficha nova.

    public Criteria withGenres(List<Integer> genres) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withExcludeGenres(List<Integer> excludeGenres) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withRuntimeMin(Integer runtimeMin) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withRuntimeMax(Integer runtimeMax) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withYearFrom(Integer yearFrom) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withYearTo(Integer yearTo) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withOriginalLanguage(String originalLanguage) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withCertificationMax(String certificationMax) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    public Criteria withGenreMatch(String genreMatch) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }

    // Marcou "todos" na tela: o título precisa ter cada um dos gêneros (faroeste E ficção científica)
    public boolean allGenres() {
        return ALL_GENRES.equals(genreMatch);
    }

    public Criteria withKeywords(List<String> keywords) {
        return new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo, originalLanguage,
                certificationMax, keywords, genreMatch);
    }
}
