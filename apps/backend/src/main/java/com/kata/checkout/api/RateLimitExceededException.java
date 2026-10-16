package com.kata.checkout.api;

import java.time.Duration;

/** El cliente superó un límite de peticiones; {@code retryAfter} dice cuánto esperar. */
class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    RateLimitExceededException(Duration retryAfter) {
        super("Límite de peticiones superado");
        this.retryAfter = retryAfter;
    }

    /** Segundos completos para el header {@code Retry-After}, redondeados hacia arriba y al menos 1. */
    long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
