package com.kata.checkout.domain.discount;

import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Paso 2: un porcentaje si el monto que dejaron los pasos anteriores supera el umbral. "Supera" es
 * estrictamente mayor: un monto igual al umbral no recibe el descuento.
 */
public final class VolumeDiscount implements DiscountStrategy {

    private final BigDecimal threshold;
    private final BigDecimal rate;

    public VolumeDiscount(BigDecimal threshold, BigDecimal rate) {
        Objects.requireNonNull(threshold, "threshold");
        if (threshold.signum() < 0) {
            throw new IllegalArgumentException("El umbral no puede ser negativo: " + threshold);
        }
        this.threshold = threshold;
        this.rate = Rates.requireRate(rate, "rate");
    }

    @Override
    public DiscountRule rule() {
        return DiscountRule.VOLUME;
    }

    @Override
    public Optional<AppliedDiscount> apply(DiscountContext context) {
        BigDecimal base = context.remaining();
        BigDecimal amount = base.multiply(rate);
        if (base.compareTo(threshold) <= 0 || amount.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedDiscount(rule(), rate, threshold.toPlainString(), base, amount));
    }
}
