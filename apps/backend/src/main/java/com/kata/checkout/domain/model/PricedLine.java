package com.kata.checkout.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Línea del carrito con el producto y el precio tomados del catálogo. */
public record PricedLine(Product product, int quantity) {

    public PricedLine {
        Objects.requireNonNull(product, "product");
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero: " + quantity);
        }
    }

    /** Exacto: precio con 2 decimales por un entero, sin redondeo. */
    public BigDecimal lineSubtotal() {
        return product.unitPrice().multiply(BigDecimal.valueOf(quantity));
    }
}
