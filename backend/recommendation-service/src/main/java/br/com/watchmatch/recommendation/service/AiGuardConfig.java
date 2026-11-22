package br.com.watchmatch.recommendation.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Números do circuit breaker e do limite suave; dá para ajustar por variável de ambiente sem recompilar
@Configuration
class AiGuardConfig {

    @Bean
    AiGuard aiGuard(
            @Value("${ai.circuit.window:10}") int window,
            @Value("${ai.circuit.minimum-calls:4}") int minimumCalls,
            @Value("${ai.circuit.failure-rate:50}") float failureRate,
            @Value("${ai.circuit.open-for:30s}") Duration openFor,
            @Value("${ai.circuit.half-open-calls:2}") int halfOpenCalls,
            @Value("${ai.rerank.permits:8}") int rerankPermits,
            @Value("${ai.rerank.permit-refresh:1m}") Duration permitRefresh) {
        return new AiGuard(new AiGuard.Settings(
                window, minimumCalls, failureRate, openFor, halfOpenCalls, rerankPermits, permitRefresh));
    }
}
