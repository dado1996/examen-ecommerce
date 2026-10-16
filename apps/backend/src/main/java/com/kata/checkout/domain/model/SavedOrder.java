package com.kata.checkout.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Identificador y fecha de creación que asignó la persistencia al guardar una orden. */
public record SavedOrder(long id, Instant createdAt) {

    public SavedOrder {
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
