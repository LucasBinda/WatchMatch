package br.com.watchmatch.catalog.tmdb;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// /search/multi: filmes, séries e pessoas misturados, na ordem de relevância do TMDB
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMultiPage(List<TmdbMultiResult> results) {

    // Os campos do TmdbResult + media_type ("movie", "tv" ou "person")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmdbMultiResult(
            Integer id,
            @JsonProperty("media_type") String mediaType,
            String title,
            String name,
            @JsonProperty("original_title") String originalTitle,
            @JsonProperty("original_name") String originalName,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("vote_average") Double voteAverage,
            @JsonProperty("genre_ids") List<Integer> genreIds,
            @JsonProperty("original_language") String originalLanguage) {

        public TmdbResult toResult() {
            return new TmdbResult(id, title, name, originalTitle, originalName, overview, posterPath, releaseDate,
                    firstAirDate, voteAverage, genreIds, originalLanguage);
        }
    }
}
