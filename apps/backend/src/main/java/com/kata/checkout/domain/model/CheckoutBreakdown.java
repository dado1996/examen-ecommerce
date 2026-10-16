package com.kata.checkout.domain.model;

import com.kata.checkout.domain.discount.DiscountResult;
import java.util.List;
import java.util.Objects;

/** Desglose de un carrito: líneas con precio del catálogo y resultado de la cascada de descuentos. */
public record CheckoutBreakdown(List<PricedLine> lines, DiscountResult result) {

    public CheckoutBreakdown {
        lines = List.copyOf(lines);
        Objects.requireNonNull(result, "result");
    }

    /** Orden lista para persistir, de invitado (sin usuario). */
    public Order toOrder() {
        return new Order(null, result.couponCode(),
                lines.stream()
                        .map(l -> new OrderLine(l.product().id(), l.quantity(), l.product().unitPrice(),
                                l.product().category()))
                        .toList(),
                result.discounts(), result.subtotal(), result.discountTotal(), result.total(),
                result.maxDiscountRate());
    }
}
