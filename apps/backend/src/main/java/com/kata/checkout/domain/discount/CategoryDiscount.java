package com.kata.checkout.domain.discount;

import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import com.kata.checkout.domain.model.PricedLine;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Optional;

/**
 * Paso 1: un porcentaje solo sobre los productos de una categoría. Es el primer paso, así que su
 * base es la suma de esas líneas en el subtotal original. Las categorías se comparan en Unicode
 * NFC, para que "Tecnología" coincida aunque la tilde venga como carácter combinante.
 */
public final class CategoryDiscount implements DiscountStrategy {

    private final String category;
    private final BigDecimal rate;

    public CategoryDiscount(String category, BigDecimal rate) {
        this.category = normalize(Rates.requireText(category, "category"));
        this.rate = Rates.requireRate(rate, "rate");
    }

    @Override
    public DiscountRule rule() {
        return DiscountRule.CATEGORY;
    }

    @Override
    public Optional<AppliedDiscount> apply(DiscountContext context) {
        BigDecimal base = context.lines().stream()
                .filter(line -> normalize(line.product().category()).equals(category))
                .map(PricedLine::lineSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = base.multiply(rate);
        if (amount.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedDiscount(rule(), rate, category, base, amount));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }
}
