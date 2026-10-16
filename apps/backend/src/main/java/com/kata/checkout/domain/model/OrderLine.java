package com.kata.checkout.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Línea de una orden confirmada, con el precio y la categoría del catálogo al momento de confirmar.
 */
public record OrderLine(long productId, int quantity, BigDecimal unitPrice, String category) {

    public OrderLine {
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(category, "category");
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero: " + quantity);
        }
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("El precio unitario debe ser mayor que cero: " + unitPrice);
        }
        if (category.isBlank()) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
    }

    public BigDecimal lineSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
