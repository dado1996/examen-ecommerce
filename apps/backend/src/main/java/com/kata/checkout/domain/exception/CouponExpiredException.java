package com.kata.checkout.domain.exception;

/** El cupón está vencido. */
public class CouponExpiredException extends RuntimeException {

    public CouponExpiredException() {
        super("El cupón está vencido");
    }
}
