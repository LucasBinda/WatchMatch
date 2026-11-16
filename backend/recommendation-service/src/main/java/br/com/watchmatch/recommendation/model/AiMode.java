package br.com.watchmatch.recommendation.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// Quanto a IA participa da recomendação (recommendation-service.yaml, campo aiMode)
public enum AiMode {

    // A IA entende o pedido, escolhe os títulos entre os candidatos e justifica cada um
    @JsonProperty("pick")
    PICK,

    // A IA só transforma o texto em filtros para o catálogo; a ordem é a do catálogo, sem justificativa
    @JsonProperty("filters")
    FILTERS
}
