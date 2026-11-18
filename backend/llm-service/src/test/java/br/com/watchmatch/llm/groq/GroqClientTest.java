package br.com.watchmatch.llm.groq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Chama o Groq pedindo JSON no modo estrito. Um Groq falso responde no lugar do real.
 */
class GroqClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer groqFalso = MockRestServiceServer.bindTo(builder).build();
    private final GroqClient client = new GroqClient(builder, "https://api.groq.com/openai/v1", "chave-de-teste");

    @Test
    void pedeJsonNoModoEstritoEDevolveOConteudo() {
        groqFalso.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer chave-de-teste"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("pedido"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("intent"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.type").value("object"))
                .andRespond(withSuccess("""
                    {"choices": [{"message": {"role": "assistant", "content": "{\\"ok\\": true}"}}],
                     "usage": {"total_tokens": 321}}
                    """, MediaType.APPLICATION_JSON));

        var resposta = client.complete("openai/gpt-oss-20b", "regras", "pedido", "intent",
                Map.of("type", "object"));

        assertThat(resposta.content()).isEqualTo("{\"ok\": true}");
        assertThat(resposta.totalTokens()).isEqualTo(321);
        groqFalso.verify();
    }

    @Test
    void respostaForaDoSchemaEAproveitadaDoFailedGeneration() {
        groqFalso.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                    {"error": {"message": "Generated JSON does not match the expected schema.",
                               "type": "invalid_request_error", "code": "json_validate_failed",
                               "failed_generation": "{\\"picks\\": [{\\"id\\": 999}]}"}}
                    """));

        var resposta = client.complete("openai/gpt-oss-120b", "regras", "pedido", "rerank",
                Map.of("type", "object"));

        assertThat(resposta.content()).isEqualTo("{\"picks\": [{\"id\": 999}]}");
        assertThat(resposta.totalTokens()).isNull();
    }

    @Test
    void outroErro400ContinuaSendoErro() {
        groqFalso.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                    {"error": {"message": "model not found", "code": "model_not_found"}}
                    """));

        assertThatThrownBy(() -> client.complete("x", "regras", "pedido", "rerank", Map.of("type", "object")))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }
}
