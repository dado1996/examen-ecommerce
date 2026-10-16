package com.kata.checkout.domain.exception;

/** No hay stock suficiente para la cantidad pedida. */
public class InsufficientStockException extends RuntimeException {

    private final long productId;
    private final int requested;
    private final int available;

    public InsufficientStockException(long productId, int requested, int available) {
        super("Stock insuficiente para el producto " + productId
                + ": se pidieron " + requested + " y hay " + available);
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public long productId() {
        return productId;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
