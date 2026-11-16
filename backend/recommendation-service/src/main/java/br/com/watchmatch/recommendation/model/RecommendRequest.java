package br.com.watchmatch.recommendation.model;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// POST /recommend (recommendation-service.yaml)
public record RecommendRequest(
        @Size(max = 500) String query,
        Criteria filters,
        @NotEmpty List<Integer> providers,
        Integer limit,
        @Min(1) @Max(10) Integer page,
        AiMode aiMode) {

    // Múltiplos de 12: a grade tem 2, 3 ou 4 colunas e a página sempre fecha a última linha
    public static final Set<Integer> PAGE_SIZES = Set.of(12, 24, 36, 48);
    public static final int DEFAULT_LIMIT = 12;

    public int limitOrDefault() {
        return limit != null ? limit : DEFAULT_LIMIT;
    }

    public int pageOrDefault() {
        return page != null ? page : 1;
    }

    public AiMode aiModeOrDefault() {
        return aiMode != null ? aiMode : AiMode.PICK;
    }

    @AssertTrue(message = "deve ser 12, 24, 36 ou 48")
    public boolean isLimitValid() {
        return limit == null || PAGE_SIZES.contains(limit);
    }
}
