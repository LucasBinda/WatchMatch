package br.com.watchmatch.recommendation.client;

import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import br.com.watchmatch.recommendation.api.RequestIdFilter;

@Configuration
public class ClientsConfig {

    @Bean
    CatalogClient catalogClient(@Value("${catalog.url}") String url) {
        // O lookup busca até 48 títulos no TMDB (em paralelo), por isso a espera é maior que o normal
        return new CatalogClient(client(url, Duration.ofSeconds(15)));
    }

    @Bean
    LlmClient llmClient(@Value("${llm.url}") String url) {
        // Com 48 títulos a IA escreve 48 justificativas: pode levar mais de 20 segundos
        return new LlmClient(client(url, Duration.ofSeconds(40)));
    }

    private RestClient client(String baseUrl, Duration readTimeout) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .requestInterceptor(forwardRequestId())
                .build();
    }

    // Repassa o X-Request-Id para os outros serviços: dá para seguir um pedido pelos logs de todos
    private ClientHttpRequestInterceptor forwardRequestId() {
        return (request, body, execution) -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                request.getHeaders().set(RequestIdFilter.HEADER, requestId);
            }
            return execution.execute(request, body);
        };
    }
}
