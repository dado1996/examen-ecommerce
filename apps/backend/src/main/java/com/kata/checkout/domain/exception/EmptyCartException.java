package com.kata.checkout.domain.exception;

/** El carrito está vacío. */
public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("El carrito está vacío");
    }
}
