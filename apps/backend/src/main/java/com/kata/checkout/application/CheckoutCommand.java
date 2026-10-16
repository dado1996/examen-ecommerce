package com.kata.checkout.application;

import com.kata.checkout.domain.model.CartItem;
import java.util.List;

/**
 * Lo que envía el cliente: productos, cantidades y cupón. Nunca precios.
 *
 * @param couponCode cupón opcional, o {@code null}
 */
public record CheckoutCommand(List<CartItem> items, String couponCode) {
}
