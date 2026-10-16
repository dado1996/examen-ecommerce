package com.kata.checkout.api.dto;

import com.kata.checkout.domain.model.AppliedDiscount;

/** Paso de la cascada. {@code baseAmount} y {@code amount} van sin redondear, como string decimal. */
public record AppliedDiscountResponse(int step, String rule, String rate, String reference, String baseAmount,
                                      String amount) {

    static AppliedDiscountResponse from(AppliedDiscount discount) {
        return new AppliedDiscountResponse(discount.rule().step(), discount.rule().name(),
                discount.rate().toPlainString(), discount.reference(), discount.baseAmount().toPlainString(),
                discount.amount().toPlainString());
    }
}
