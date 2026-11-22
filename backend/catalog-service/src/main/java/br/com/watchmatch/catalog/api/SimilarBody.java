package br.com.watchmatch.catalog.api;

import java.util.List;

import br.com.watchmatch.catalog.domain.Criteria;
import br.com.watchmatch.catalog.domain.SimilarRequest;
import br.com.watchmatch.catalog.domain.TitleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// O JSON do SimilarRequest do contrato: o título citado + os mesmos campos do DiscoverRequest (sem sortBy)
public record SimilarBody(
        @NotBlank @Size(max = 100) String title,
        TitleType mediaType,
        List<Integer> genres,
        List<Integer> excludeGenres,
        @Min(0) Integer runtimeMin,
        Integer runtimeMax,
        Integer yearFrom,
        Integer yearTo,
        @Pattern(regexp = "[a-z]{2}", message = "deve ser um código ISO 639-1, como ko") String originalLanguage,
        String certificationMax,
        @Size(max = 5) List<String> keywords,
        @NotEmpty List<Integer> providers,
        @Min(1) @Max(60) Integer limit,
        @Min(1) @Max(500) Integer page) {

    private static final int DEFAULT_LIMIT = 40;

    public SimilarRequest toDomain() {
        var criteria = new Criteria(genres, excludeGenres, runtimeMin, runtimeMax, yearFrom, yearTo,
                originalLanguage, certificationMax, keywords);
        return new SimilarRequest(title, mediaType, criteria, providers, page);
    }

    public int limitOrDefault() {
        return limit != null ? limit : DEFAULT_LIMIT;
    }
}
