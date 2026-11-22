package br.com.watchmatch.recommendation.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Formatos do llm-service.yaml que este serviço usa
public final class Llm {

    private Llm() {
    }

    public record IntentRequest(String text, List<Genre> genres, List<Provider> providers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntentResponse(
            Criteria criteria,
            List<Integer> providers,
            String referenceTitle,
            String summary,
            Boolean offTopic) {

        // A mesma resposta com outros critérios (usado ao afrouxar a busca)
        public IntentResponse withCriteria(Criteria criteria) {
            return new IntentResponse(criteria, providers, referenceTitle, summary, offTopic);
        }
    }

    public record Candidate(Integer id, String mediaType, String title, Integer year, String overview,
            List<String> genres) {
    }

    public record RerankRequest(String text, String summary, List<Candidate> candidates, Integer limit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pick(Integer id, String mediaType, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankResponse(List<Pick> picks, Integer discarded) {
    }
}
