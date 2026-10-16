package com.kata.checkout.domain.discount;

import com.kata.checkout.domain.exception.CouponExpiredException;
import com.kata.checkout.domain.exception.InvalidCouponException;
import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Paso 3: un porcentaje sobre el monto que dejaron los pasos anteriores si el cliente envía el
 * cupón. El código se compara ignorando mayúsculas y espacios al inicio y al final, y el paso
 * registra el código tal como está configurado.
 */
public final class CouponDiscount implements DiscountStrategy {

    private final String code;
    private final BigDecimal rate;
    private final Instant expiresAt;
    private final Clock clock;

    /**
     * @param expiresAt instante desde el cual el cupón deja de ser válido
     */
    public CouponDiscount(String code, BigDecimal rate, Instant expiresAt, Clock clock) {
        this.code = Rates.requireText(code, "code").strip();
        this.rate = Rates.requireRate(rate, "rate");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DiscountRule rule() {
        return DiscountRule.COUPON;
    }

    /**
     * @throws InvalidCouponException si el cupón enviado no es el registrado
     * @throws CouponExpiredException si es el registrado pero ya venció
     */
    @Override
    public Optional<AppliedDiscount> apply(DiscountContext context) {
        if (context.couponCode() == null) {
            return Optional.empty();
        }
        if (!normalize(context.couponCode()).equals(normalize(code))) {
            throw new InvalidCouponException();
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw new CouponExpiredException();
        }
        BigDecimal base = context.remaining();
        BigDecimal amount = base.multiply(rate);
        if (amount.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedDiscount(rule(), rate, code, base, amount));
    }

    private static String normalize(String text) {
        return text.strip().toUpperCase(Locale.ROOT);
    }
}
