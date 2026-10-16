package com.kata.checkout.api.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.kata.checkout.application.ConfirmedCheckout;
import java.time.Instant;

/** Checkout guardado: el desglose más {@code id} y {@code createdAt}. */
public record CheckoutResponse(long id, Instant createdAt, @JsonUnwrapped CheckoutBreakdownResponse breakdown) {

    public static CheckoutResponse from(ConfirmedCheckout checkout) {
        return new CheckoutResponse(checkout.id(), checkout.createdAt(),
                CheckoutBreakdownResponse.from(checkout.breakdown()));
    }
}
