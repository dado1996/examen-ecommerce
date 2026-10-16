package com.kata.checkout.domain.discount;

import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import com.kata.checkout.domain.model.PricedLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Motor de descuentos acumulativos. Ejecuta las reglas en cascada multiplicativa (cada una sobre
 * el monto que dejaron las anteriores) en el orden de {@link DiscountRule} y al final aplica el
 * tope: si el descuento acumulado supera {@code subtotal × maxTotalRate}, se trunca exactamente
 * ahí y se registra el paso {@link DiscountRule#CAP} con el exceso recortado.
 *
 * <p>Todo el cálculo es exacto con {@link BigDecimal}; solo los totales finales se redondean a 2
 * decimales: HALF_EVEN, salvo cuando se aplica el tope. Ahí el descuento se redondea hacia abajo
 * ({@link RoundingMode#DOWN}), porque la regla de negocio prevalece: nunca supera el tope
 * (p. ej. 35% de 100.10 = 35.035 → 35.03, no 35.04).
 */
public final class DiscountEngine {

    private static final int MONEY_SCALE = 2;

    private final List<DiscountStrategy> strategies;
    private final BigDecimal maxTotalRate;

    public DiscountEngine(List<DiscountStrategy> strategies, BigDecimal maxTotalRate) {
        Set<DiscountRule> rules = EnumSet.noneOf(DiscountRule.class);
        for (DiscountStrategy strategy : strategies) {
            if (strategy.rule() == DiscountRule.CAP || !rules.add(strategy.rule())) {
                throw new IllegalArgumentException("Regla repetida o no permitida: " + strategy.rule());
            }
        }
        this.strategies = strategies.stream().sorted(Comparator.comparingInt(s -> s.rule().step())).toList();
        this.maxTotalRate = Rates.requireRate(maxTotalRate, "maxTotalRate");
        // Un tope de 0 generaría un paso CAP con tasa 0, que checkout_discounts rechaza (rate > 0)
        if (maxTotalRate.signum() == 0) {
            throw new IllegalArgumentException("El tope del descuento debe ser mayor que cero");
        }
    }

    /**
     * @param couponCode cupón enviado por el cliente, o {@code null}
     */
    public DiscountResult apply(List<PricedLine> lines, String couponCode) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("No se calculan descuentos sobre un carrito vacío");
        }
        BigDecimal subtotal = lines.stream().map(PricedLine::lineSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AppliedDiscount> applied = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        for (DiscountStrategy strategy : strategies) {
            var context = new DiscountContext(lines, subtotal, subtotal.subtract(accumulated), couponCode);
            var discount = strategy.apply(context);
            if (discount.isPresent()) {
                applied.add(discount.get());
                accumulated = accumulated.add(discount.get().amount());
            }
        }

        BigDecimal cap = subtotal.multiply(maxTotalRate);
        RoundingMode discountRounding = RoundingMode.HALF_EVEN;
        if (accumulated.compareTo(cap) > 0) {
            applied.add(new AppliedDiscount(DiscountRule.CAP, maxTotalRate, null, accumulated,
                    accumulated.subtract(cap)));
            accumulated = cap;
            discountRounding = RoundingMode.DOWN;
        }

        BigDecimal roundedSubtotal = subtotal.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal discountTotal = accumulated.setScale(MONEY_SCALE, discountRounding);
        return new DiscountResult(applied, roundedSubtotal, discountTotal, roundedSubtotal.subtract(discountTotal),
                maxTotalRate);
    }
}
