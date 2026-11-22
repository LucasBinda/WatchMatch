package br.com.watchmatch.catalog.tmdb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.watchmatch.catalog.domain.Genre;
import br.com.watchmatch.catalog.tmdb.TmdbKeywordPage.TmdbKeyword;

// O título citado em "parecido com X": nome, gêneros e palavras-chave (append_to_response=keywords).
// Filme guarda as palavras em keywords.keywords; série, em keywords.results.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbReference(Integer id, String title, String name, List<Genre> genres, Keywords keywords) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Keywords(List<TmdbKeyword> keywords, List<TmdbKeyword> results) {
    }

    public String displayTitle() {
        return title != null ? title : name;
    }

    public List<Integer> genreIds() {
        return genres == null ? List.of() : genres.stream().map(Genre::id).toList();
    }

    public List<Integer> keywordIds() {
        if (keywords == null) {
            return List.of();
        }
        List<TmdbKeyword> list = keywords.keywords() != null ? keywords.keywords() : keywords.results();
        return list == null ? List.of() : list.stream().map(TmdbKeyword::id).toList();
    }
}
