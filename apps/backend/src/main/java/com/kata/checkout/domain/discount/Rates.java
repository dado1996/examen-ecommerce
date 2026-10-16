package com.kata.checkout.domain.discount;

import java.math.BigDecimal;
import java.util.Objects;

/** Validaciones comunes de los parámetros de las reglas. */
final class Rates {

    private Rates() {
    }

    /** @throws IllegalArgumentException si la tasa no está en [0, 1] */
    static BigDecimal requireRate(BigDecimal rate, String name) {
        Objects.requireNonNull(rate, name);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("La tasa " + name + " debe estar entre 0 y 1: " + rate);
        }
        return rate;
    }

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException("El valor " + name + " es obligatorio");
        }
        return value;
    }
}
