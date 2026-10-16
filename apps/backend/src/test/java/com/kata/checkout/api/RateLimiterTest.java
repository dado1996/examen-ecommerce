package com.kata.checkout.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Límite de 5 permisos por minuto: se recupera uno cada 12 segundos. */
class RateLimiterTest {

    private final AtomicLong ahora = new AtomicLong(1_000_000_000L);
    private final RateLimiter limiter = new RateLimiter(5, Duration.ofMinutes(1), 3, ahora::get);

    @Test
    void permiteHastaElLimiteSeguidoYLuegoRechaza() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        }

        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void unaPeticionRechazadaNoConsumePermisos() {
        agotar("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.1");

        avanzar(Duration.ofSeconds(12));

        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
    }

    @Test
    void recuperaUnPermisoCadaFraccionDeLaVentana() {
        agotar("10.0.0.1");

        avanzar(Duration.ofSeconds(5));
        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(Duration.ofSeconds(7));

        avanzar(Duration.ofSeconds(7));
        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void trasUnaVentanaCompletaSinPeticionesVuelveATenerElLimiteEntero() {
        agotar("10.0.0.1");

        avanzar(Duration.ofMinutes(10));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        }
        assertThat(limiter.tryAcquire("10.0.0.1")).isPositive();
    }

    @Test
    void cadaClaveTieneSuPropioLimite() {
        agotar("10.0.0.1");

        assertThat(limiter.tryAcquire("10.0.0.2")).isZero();
    }

    @Test
    void registrarConsumeSiempreYEsperaNoConsume() {
        assertThat(limiter.waitTime("10.0.0.1")).isZero();

        for (int i = 0; i < 7; i++) {
            limiter.record("10.0.0.1");
        }

        // 7 registros con capacidad 5: hay que esperar 3 permisos (36 s) para tener uno libre
        assertThat(limiter.waitTime("10.0.0.1")).isEqualTo(Duration.ofSeconds(36));
        assertThat(limiter.waitTime("10.0.0.1")).isEqualTo(Duration.ofSeconds(36));
    }

    @Test
    void olvidaLasClavesInactivasCuandoSuperaElMaximoParaNoCrecerSinLimite() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.2");
        limiter.tryAcquire("10.0.0.3");
        avanzar(Duration.ofMinutes(1));

        limiter.tryAcquire("10.0.0.4");

        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    @Test
    void conservaLasClavesActivasAunqueSupereElMaximo() {
        limiter.tryAcquire("10.0.0.1");
        limiter.tryAcquire("10.0.0.2");
        limiter.tryAcquire("10.0.0.3");

        limiter.tryAcquire("10.0.0.4");

        assertThat(limiter.trackedKeys()).isEqualTo(4);
    }

    @Test
    void rechazaUnaConfiguracionSinPermisosOSinVentana() {
        assertThatThrownBy(() -> new RateLimiter(0, Duration.ofMinutes(1), 10, ahora::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(5, Duration.ZERO, 10, ahora::get))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void agotar(String clave) {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(clave);
        }
    }

    private void avanzar(Duration tiempo) {
        ahora.addAndGet(tiempo.toNanos());
    }
}
