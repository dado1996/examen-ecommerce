package com.kata.checkout.domain.exception;

/** La clave de idempotencia ya se usó con otro pedido. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("La clave de idempotencia ya se usó con otro pedido");
    }
}
