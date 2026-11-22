package br.com.watchmatch.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import br.com.watchmatch.recommendation.service.AiGuard.AiUnavailableException;
import br.com.watchmatch.recommendation.service.AiGuard.Reason;
import br.com.watchmatch.recommendation.service.AiGuard.Settings;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;

class AiGuardTest {

    // Os mesmos números do padrão, mas com tempos curtos para o teste não esperar 30 s
    private static final Settings RAPIDO =
            new Settings(10, 4, 50, Duration.ofMillis(100), 2, 8, Duration.ofMillis(200));

    @Test
    void fichasCobradasPeloTamanhoDaPagina() {
        assertThat(AiGuard.permitsFor(12)).isEqualTo(1);
        assertThat(AiGuard.permitsFor(24)).isEqualTo(2);
        assertThat(AiGuard.permitsFor(36)).isEqualTo(3);
        assertThat(AiGuard.permitsFor(48)).isEqualTo(4);
        assertThat(AiGuard.permitsFor(2)).isEqualTo(1);
    }

    @Test
    void circuitoAbreDepoisDasFalhasEFechaQuandoAIaVolta() throws InterruptedException {
        AiGuard guard = new AiGuard(RAPIDO);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.intent(() -> { throw new ResourceAccessException("timeout"); }))
                    .isInstanceOf(ResourceAccessException.class);
        }
        assertThat(guard.intentState()).isEqualTo(State.OPEN);

        AtomicInteger chamadas = new AtomicInteger();
        assertThatThrownBy(() -> guard.intent(chamadas::incrementAndGet))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.PAUSED));
        assertThat(chamadas).hasValue(0);

        // Passado o tempo aberto, deixa passar 2 chamadas de teste; deram certo, fecha
        Thread.sleep(150);
        guard.intent(chamadas::incrementAndGet);
        assertThat(guard.intentState()).isEqualTo(State.HALF_OPEN);
        guard.intent(chamadas::incrementAndGet);
        assertThat(guard.intentState()).isEqualTo(State.CLOSED);
        assertThat(chamadas).hasValue(2);
    }

    @Test
    void circuitosDoIntentEDoRerankSaoSeparados() {
        AiGuard guard = new AiGuard(RAPIDO);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> guard.rerank(12, () -> {
                throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
            })).isInstanceOf(HttpServerErrorException.class);
        }

        assertThat(guard.rerankState()).isEqualTo(State.OPEN);
        assertThat(guard.intentState()).isEqualTo(State.CLOSED);
        assertThat(guard.intent(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void semFichaNaoChamaEAsFichasVoltamNoProximoPeriodo() throws InterruptedException {
        AiGuard guard = new AiGuard(RAPIDO);
        guard.rerank(48, () -> "1");
        guard.rerank(48, () -> "2");

        assertThatThrownBy(() -> guard.rerank(12, () -> "3"))
                .isInstanceOfSatisfying(AiUnavailableException.class, e -> assertThat(e.reason()).isEqualTo(Reason.BUSY));
        // Ficar sem ficha não é falha da IA: o circuito continua fechado
        assertThat(guard.rerankState()).isEqualTo(State.CLOSED);

        Thread.sleep(250);
        assertThat(guard.rerank(48, () -> "4")).isEqualTo("4");
    }
}
