package com.kata.checkout.domain.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Orden confirmada lista para persistir. Los totales son finales (redondeados por el motor de
 * descuentos); {@code maxDiscountRate} es la copia del tope vigente al confirmar.
 *
 * @param userId     {@code null} en un checkout de invitado
 * @param couponCode cupón aplicado, o {@code null}
 */
public record Order(Long userId, String couponCode, List<OrderLine> lines, List<AppliedDiscount> discounts,
                    BigDecimal subtotal, BigDecimal discountTotal, BigDecimal total, BigDecimal maxDiscountRate) {

    public Order {
        lines = List.copyOf(lines);
        discounts = List.copyOf(discounts);
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(discountTotal, "discountTotal");
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(maxDiscountRate, "maxDiscountRate");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("El carrito está vacío");
        }
        Set<Long> productIds = new HashSet<>();
        for (OrderLine line : lines) {
            if (!productIds.add(line.productId())) {
                throw new IllegalArgumentException(
                        "Producto repetido; hay que consolidar las cantidades: " + line.productId());
            }
        }
        if (subtotal.subtract(discountTotal).compareTo(total) != 0) {
            throw new IllegalArgumentException("El total no es el subtotal menos el descuento");
        }
    }

    public boolean capApplied() {
        return discounts.stream().anyMatch(d -> d.rule() == DiscountRule.CAP);
    }
}
