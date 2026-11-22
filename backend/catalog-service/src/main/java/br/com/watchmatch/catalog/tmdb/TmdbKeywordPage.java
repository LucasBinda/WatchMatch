package br.com.watchmatch.catalog.tmdb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// /search/keyword: as palavras-chave do TMDB que batem com o texto ("heist" → id 10051)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbKeywordPage(List<TmdbKeyword> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmdbKeyword(Integer id, String name) {
    }
}
