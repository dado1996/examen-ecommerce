package com.kata.checkout.domain.exception;

/** El cupón no está registrado. */
public class InvalidCouponException extends RuntimeException {

    public InvalidCouponException() {
        super("El cupón no está registrado");
    }
}
