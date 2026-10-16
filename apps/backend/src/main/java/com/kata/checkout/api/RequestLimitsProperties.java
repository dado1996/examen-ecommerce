package com.kata.checkout.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Límites de las peticiones a la API ({@code app.limits} en {@code application.yml}).
 *
 * @param maxBodySize    tamaño máximo del cuerpo en {@code /api/**}
 * @param checkoutRate   peticiones por IP a {@code POST /checkouts} y {@code /checkouts/preview}, juntas
 * @param couponFailures cupones rechazados por IP antes de bloquearla en esos endpoints
 */
@Validated
@ConfigurationProperties(prefix = "app.limits")
public record RequestLimitsProperties(
        @NotNull DataSize maxBodySize,
        @Valid @NotNull Rate checkoutRate,
        @Valid @NotNull Rate couponFailures) {

    public record Rate(@Positive int requests, @NotNull Duration window) {
    }
}
