package br.com.watchmatch.llm.groq;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Fala com a API do Groq (compatível com a da OpenAI): POST /chat/completions
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    // Erro do Groq quando a resposta da IA não segue o schema (ex.: um id fora do enum)
    static final String SCHEMA_MISMATCH = "json_validate_failed";

    private final RestClient restClient;

    public GroqClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    // Pede uma resposta em JSON que segue o schema à risca (structured outputs, modo estrito)
    public Completion complete(String model, String system, String user, String schemaName, Map<String, Object> schema) {
        var request = new ChatRequest(
                model,
                List.of(new Message("system", system), new Message("user", user)),
                0.2,
                "low",
                new ResponseFormat("json_schema", new JsonSchema(schemaName, true, schema)));

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (HttpClientErrorException.BadRequest e) {
            return salvage(e);
        }

        String content = response.choices().get(0).message().content();
        Integer tokens = response.usage() != null ? response.usage().totalTokens() : null;
        return new Completion(content, tokens);
    }

    // No modo estrito o Groq confere o JSON depois de gerado: se um único campo sai do schema (um id fora da
    // lista entre 48 escolhas, por exemplo), ele recusa a resposta inteira, mas a devolve em failed_generation.
    // Aproveitamos esse texto: os serviços já conferem cada campo e descartam o que não vale (grounding).
    private Completion salvage(HttpClientErrorException.BadRequest e) {
        ErrorResponse error = e.getResponseBodyAs(ErrorResponse.class);
        if (error == null || error.error() == null || !SCHEMA_MISMATCH.equals(error.error().code())
                || error.error().failedGeneration() == null || error.error().failedGeneration().isBlank()) {
            throw e;
        }
        log.info("Groq recusou a resposta por sair do schema; usando failed_generation: {}", error.error().message());
        return new Completion(error.error().failedGeneration(), null);
    }

    public record Completion(String content, Integer totalTokens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(ErrorBody error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(String message, String code,
            @JsonProperty("failed_generation") String failedGeneration) {
    }

    // temperature baixa = respostas mais previsíveis; reasoning_effort "low" = os modelos gpt-oss pensam menos e respondem mais rápido
    record ChatRequest(
            String model,
            List<Message> messages,
            Double temperature,
            @JsonProperty("reasoning_effort") String reasoningEffort,
            @JsonProperty("response_format") ResponseFormat responseFormat) {
    }

    record Message(String role, String content) {
    }

    record ResponseFormat(String type, @JsonProperty("json_schema") JsonSchema jsonSchema) {
    }

    record JsonSchema(String name, Boolean strict, Map<String, Object> schema) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("total_tokens") Integer totalTokens) {
    }
}
