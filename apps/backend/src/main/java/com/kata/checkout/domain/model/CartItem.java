package com.kata.checkout.domain.model;

/** Lo único que pide el cliente por cada producto: cuál y cuántas unidades. */
public record CartItem(long productId, int quantity) {

    public CartItem {
        if (productId <= 0) {
            throw new IllegalArgumentException("El identificador del producto debe ser positivo: " + productId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero: " + quantity);
        }
    }
}
