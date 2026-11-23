package br.com.watchmatch.recommendation.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

// Protege as chamadas à IA (docs/ARQUITETURA.md, "Resiliência"):
//  - um circuit breaker para o /intent e outro para o /rerank. Com falhas demais o circuito abre e, por um
//    tempo, a busca nem tenta a IA: responde na hora, sem esperar o timeout. Se só o /rerank (o pesado) cair,
//    a IA ainda entende o pedido e a busca vira o modo "filters";
//  - um limite suave no /rerank, em fichas por minuto para o site todo, cobradas pelo tamanho da página
//    (12 títulos = 1 ficha, 48 = 4). Sem ficha, a busca não falha: vem na ordem do catálogo.
//  - 503 do llm-service = o Groq recusou por limite de uso (429). A IA está no ar, só ocupada: vira BUSY,
//    como o limite suave, e não conta para abrir o circuito.
public class AiGuard {

    private static final Logger log = LoggerFactory.getLogger(AiGuard.class);

    static final int TITLES_PER_PERMIT = 12;

    public enum Reason {
        // Circuito aberto: a IA falhou muito há pouco
        PAUSED,
        // Limite suave sem fichas, ou o Groq no limite de uso por minuto
        BUSY
    }

    // A IA não foi chamada; a busca segue sem ela
    public static class AiUnavailableException extends RuntimeException {
        private final Reason reason;

        AiUnavailableException(Reason reason) {
            super("IA não chamada: " + reason, null, false, false);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    public record Settings(
            // Circuito: olha as últimas `window` chamadas e abre com `failureRate`% de falha (mín. `minimumCalls`)
            int window, int minimumCalls, float failureRate,
            // Fica aberto `openFor` e depois deixa passar `halfOpenCalls` chamadas de teste
            Duration openFor, int halfOpenCalls,
            // Limite suave do /rerank: `rerankPermits` fichas a cada `permitRefresh`
            int rerankPermits, Duration permitRefresh) {

        public static final Settings DEFAULTS =
                new Settings(10, 4, 50, Duration.ofSeconds(30), 2, 4, Duration.ofMinutes(1));
        // 4 fichas: o rerank de uma página de 12 gasta ~2.000 tokens e o Groq grátis aceita 8.000 por minuto
    }

    private final CircuitBreaker intentBreaker;
    private final CircuitBreaker rerankBreaker;
    private final RateLimiter rerankLimiter;

    public AiGuard(Settings settings) {
        CircuitBreakerConfig breakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.window())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRate())
                .waitDurationInOpenState(settings.openFor())
                .permittedNumberOfCallsInHalfOpenState(settings.halfOpenCalls())
                // 4xx é erro do nosso pedido (bug), não da IA: não conta para abrir o circuito.
                // Conta: 5xx (o llm-service devolve 502 quando o Groq falha ou estoura a cota), timeout, conexão
                .recordException(e -> !(e instanceof HttpClientErrorException))
                .build();
        this.intentBreaker = breaker("llm-intent", breakerConfig);
        this.rerankBreaker = breaker("llm-rerank", breakerConfig);
        this.rerankLimiter = RateLimiter.of("llm-rerank", RateLimiterConfig.custom()
                .limitForPeriod(settings.rerankPermits())
                .limitRefreshPeriod(settings.permitRefresh())
                // Sem ficha, não espera: segue sem a IA
                .timeoutDuration(Duration.ZERO)
                .build());
    }

    // Lança AiUnavailableException(PAUSED) com o circuito aberto, ou a própria falha da chamada
    public <T> T intent(Supplier<T> call) {
        if (!intentBreaker.tryAcquirePermission()) {
            throw new AiUnavailableException(Reason.PAUSED);
        }
        return run(intentBreaker, call);
    }

    // Lança AiUnavailableException(PAUSED ou BUSY), ou a própria falha da chamada
    public <T> T rerank(int limit, Supplier<T> call) {
        if (!rerankBreaker.tryAcquirePermission()) {
            throw new AiUnavailableException(Reason.PAUSED);
        }
        if (!rerankLimiter.acquirePermission(permitsFor(limit))) {
            // A chamada não aconteceu: devolve a vaga do circuito
            rerankBreaker.releasePermission();
            throw new AiUnavailableException(Reason.BUSY);
        }
        return run(rerankBreaker, call);
    }

    static int permitsFor(int limit) {
        return Math.max(1, Math.ceilDiv(limit, TITLES_PER_PERMIT));
    }

    CircuitBreaker.State intentState() {
        return intentBreaker.getState();
    }

    CircuitBreaker.State rerankState() {
        return rerankBreaker.getState();
    }

    private static <T> T run(CircuitBreaker breaker, Supplier<T> call) {
        long start = System.nanoTime();
        try {
            T result = call.get();
            breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return result;
        } catch (HttpServerErrorException e) {
            if (e.getStatusCode().value() == 503) {
                // Não é falha nem sucesso: devolve a vaga do circuito sem registrar nada
                breaker.releasePermission();
                throw new AiUnavailableException(Reason.BUSY);
            }
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            throw e;
        } catch (RuntimeException e) {
            breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
            throw e;
        }
    }

    private static CircuitBreaker breaker(String name, CircuitBreakerConfig config) {
        CircuitBreaker breaker = CircuitBreaker.of(name, config);
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("circuito {}: {}", name, event.getStateTransition()));
        return breaker;
    }
}
