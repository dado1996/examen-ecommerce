package com.kata.checkout.application;

import com.kata.checkout.domain.model.CheckoutBreakdown;
import java.time.Instant;

/** Checkout guardado: el desglose más el identificador y la fecha que asignó la persistencia. */
public record ConfirmedCheckout(long id, Instant createdAt, CheckoutBreakdown breakdown) {
}
