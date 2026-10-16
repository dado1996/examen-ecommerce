package com.kata.checkout.domain.discount;

import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import java.util.Optional;

/**
 * Una regla de la cascada (patrón Strategy). Cada regla calcula su descuento sobre el monto que
 * dejaron las anteriores y no conoce a las demás.
 */
public interface DiscountStrategy {

    /** Posición de la regla en la cascada; el motor las ejecuta en este orden. */
    DiscountRule rule();

    /** El descuento de este paso, o vacío si la regla no tiene efecto (monto cero). */
    Optional<AppliedDiscount> apply(DiscountContext context);
}
