package com.kata.checkout.domain.exception;

/** El producto no existe en el catálogo o está inactivo. */
public class ProductNotFoundException extends RuntimeException {

    private final long productId;

    public ProductNotFoundException(long productId) {
        super("El producto " + productId + " no existe o está inactivo");
        this.productId = productId;
    }

    public long productId() {
        return productId;
    }
}
