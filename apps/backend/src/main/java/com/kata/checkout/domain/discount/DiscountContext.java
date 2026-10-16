package com.kata.checkout.domain.discount;

import com.kata.checkout.domain.model.PricedLine;
import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que ve cada regla de la cascada.
 *
 * @param subtotal   subtotal original del carrito, sin redondear
 * @param remaining  monto después de los pasos anteriores, sin redondear
 * @param couponCode cupón enviado por el cliente, o {@code null}
 */
public record DiscountContext(List<PricedLine> lines, BigDecimal subtotal, BigDecimal remaining, String couponCode) {
}
