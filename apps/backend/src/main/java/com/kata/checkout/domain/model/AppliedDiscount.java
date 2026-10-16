package com.kata.checkout.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Un paso de la cascada que tuvo efecto. {@code baseAmount} y {@code amount} van sin redondear;
 * en el paso {@link DiscountRule#CAP}, {@code amount} es el exceso recortado.
 *
 * @param reference categoría, umbral o cupón según la regla; {@code null} en el tope
 */
public record AppliedDiscount(DiscountRule rule, BigDecimal rate, String reference,
                              BigDecimal baseAmount, BigDecimal amount) {

    public AppliedDiscount {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(baseAmount, "baseAmount");
        Objects.requireNonNull(amount, "amount");
    }
}
