package br.com.watchmatch.llm.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import br.com.watchmatch.llm.service.LlmOutputException;

// Falhas do Groq viram 502, como pede o contrato. O recommendation-service segue sem a IA.
@RestControllerAdvice
public class LlmErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LlmErrorHandler.class);

    // O Groq recusou por limite de tokens ou de pedidos (429): a IA está no ar, só ocupada. 503 com Retry-After
    // deixa o recommendation-service mostrar "muita gente buscando" sem contar isso como falha no circuito
    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<ProblemDetail> groqBusy(HttpClientErrorException.TooManyRequests e) {
        String retryAfter = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER) : null;
        log.info("Groq ocupado (429), tentar de novo em {} s", retryAfter);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A IA (Groq) está no limite de uso por minuto.");
        problem.setTitle("IA ocupada");
        var response = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
        if (retryAfter != null && retryAfter.matches("[0-9]+(\\.[0-9]+)?")) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf((int) Math.ceil(Double.parseDouble(retryAfter))));
        }
        return response.body(problem);
    }

    @ExceptionHandler(RestClientException.class)
    public ProblemDetail groqFailed(RestClientException e) {
        log.warn("Groq falhou: {}", e.getMessage());
        return problem("A IA (Groq) não respondeu.");
    }

    @ExceptionHandler(LlmOutputException.class)
    public ProblemDetail invalidOutput(LlmOutputException e) {
        log.warn("Resposta inválida da IA: {}", e.getMessage());
        return problem("A IA respondeu num formato inválido.");
    }

    private ProblemDetail problem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, detail);
        problem.setTitle("Falha na IA");
        return problem;
    }
}
