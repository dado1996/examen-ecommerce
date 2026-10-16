package com.kata.checkout.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Producto activo del catálogo. Su precio es la única fuente de verdad: el cliente nunca lo envía. */
public record Product(long id, String name, String category, BigDecimal unitPrice) {

    public Product {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (category.isBlank()) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("El precio unitario debe ser mayor que cero: " + unitPrice);
        }
    }
}
