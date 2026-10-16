package com.kata.checkout.api.dto;

import com.kata.checkout.domain.model.CheckoutBreakdown;
import java.util.List;

/** Desglose completo: el frontend lo muestra tal cual, sin repetir el cálculo. */
public record CheckoutBreakdownResponse(String couponCode, List<CheckoutLineResponse> lines,
                                        List<AppliedDiscountResponse> discounts, String subtotal,
                                        String discountTotal, String total, String maxDiscountRate,
                                        boolean capApplied) {

    public static CheckoutBreakdownResponse from(CheckoutBreakdown breakdown) {
        var result = breakdown.result();
        return new CheckoutBreakdownResponse(result.couponCode(),
                breakdown.lines().stream().map(CheckoutLineResponse::from).toList(),
                result.discounts().stream().map(AppliedDiscountResponse::from).toList(),
                result.subtotal().toPlainString(), result.discountTotal().toPlainString(),
                result.total().toPlainString(), result.maxDiscountRate().toPlainString(), result.capApplied());
    }
}
