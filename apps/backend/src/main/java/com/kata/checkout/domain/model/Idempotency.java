package com.kata.checkout.domain.model;

import java.util.Objects;

/**
 * Clave de idempotencia de un checkout y el hash del pedido con el que se usó. Con el hash se
 * distingue un reintento (mismo pedido) de una clave reutilizada con otro carrito o cupón.
 *
 * @param key         UUID canónico en minúsculas
 * @param requestHash SHA-256 hexadecimal del pedido normalizado
 */
public record Idempotency(String key, String requestHash) {

    public Idempotency {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestHash, "requestHash");
        if (key.isBlank()) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria");
        }
    }
}
