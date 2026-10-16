package com.kata.checkout.domain.model;

/** Pasos de la cascada de descuentos, en su orden de precedencia. */
public enum DiscountRule {
    CATEGORY(1),
    VOLUME(2),
    COUPON(3),
    CAP(4);

    private final int step;

    DiscountRule(int step) {
        this.step = step;
    }

    public int step() {
        return step;
    }
}
