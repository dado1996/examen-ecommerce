/**
 * Motor de descuentos acumulativos. Cada regla implementa {@link com.kata.checkout.domain.discount.DiscountStrategy}
 * (patrón Strategy) y {@link com.kata.checkout.domain.discount.DiscountEngine} las encadena en cascada y aplica el
 * tope. Los valores de las reglas los entrega la fábrica de infraestructura desde {@code discounts.*}.
 */
package com.kata.checkout.domain.discount;
