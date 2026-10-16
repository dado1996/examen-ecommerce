package com.kata.checkout.domain.discount;

import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de la cascada. Los pasos van sin redondear; {@code subtotal}, {@code discountTotal} y
 * {@code total} son los totales finales, redondeados HALF_EVEN a 2 decimales.
 */
public record DiscountResult(List<AppliedDiscount> discounts, BigDecimal subtotal, BigDecimal discountTotal,
                             BigDecimal total, BigDecimal maxDiscountRate) {

    public DiscountResult {
        discounts = List.copyOf(discounts);
    }

    public boolean capApplied() {
        return discounts.stream().anyMatch(d -> d.rule() == DiscountRule.CAP);
    }

    /** Cupón aplicado tal como está configurado, o {@code null} si no hubo. */
    public String couponCode() {
        return discounts.stream()
                .filter(d -> d.rule() == DiscountRule.COUPON)
                .map(AppliedDiscount::reference)
                .findFirst()
                .orElse(null);
    }
}
