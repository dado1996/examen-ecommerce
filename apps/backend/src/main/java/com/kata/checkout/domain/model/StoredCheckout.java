package com.kata.checkout.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Checkout ya guardado con una clave de idempotencia: lo necesario para responder un reintento
 * igual que la primera vez, sin volver a validar ni reservar stock.
 */
public record StoredCheckout(long id, Instant createdAt, String requestHash, CheckoutBreakdown breakdown) {

    public StoredCheckout {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(requestHash, "requestHash");
        Objects.requireNonNull(breakdown, "breakdown");
    }
}
