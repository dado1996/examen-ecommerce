package com.kata.checkout.infrastructure.config;

import com.kata.checkout.domain.discount.CategoryDiscount;
import com.kata.checkout.domain.discount.CouponDiscount;
import com.kata.checkout.domain.discount.DiscountEngine;
import com.kata.checkout.domain.discount.VolumeDiscount;
import java.time.Clock;
import java.util.List;

/**
 * Fábrica del motor (patrón Factory): traduce {@code discounts.*} a las reglas del dominio en el
 * orden de la cascada. Es el único lugar que conoce a la vez la configuración y las reglas.
 */
public final class DiscountEngineFactory {

    private DiscountEngineFactory() {
    }

    public static DiscountEngine create(DiscountProperties properties, Clock clock) {
        return new DiscountEngine(List.of(
                new CategoryDiscount(properties.category().name(), properties.category().rate()),
                new VolumeDiscount(properties.volume().threshold(), properties.volume().rate()),
                new CouponDiscount(properties.coupon().code(), properties.coupon().rate(),
                        properties.coupon().expiresAt(), clock)),
                properties.maxTotalRate());
    }
}
