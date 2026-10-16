package com.kata.checkout.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.discount.DiscountEngine;
import com.kata.checkout.domain.discount.DiscountResult;
import com.kata.checkout.domain.exception.CouponExpiredException;
import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import com.kata.checkout.domain.model.PricedLine;
import com.kata.checkout.domain.model.Product;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscountEngineFactoryTest {

    private static final Instant VENCE = Instant.parse("2027-01-01T00:00:00Z");
    private static final List<PricedLine> DOS_LAPTOPS = List.of(
            new PricedLine(new Product(1, "Laptop", "Tecnología", new BigDecimal("1200.00")), 2));

    @Test
    void construyeLasReglasConLosValoresDeLaConfiguracion() {
        DiscountEngine engine = DiscountEngineFactory.create(propiedades("0.10", "0.35"), antes(VENCE));

        DiscountResult resultado = engine.apply(DOS_LAPTOPS, "WELCOME2026");

        assertThat(resultado.discounts()).extracting(AppliedDiscount::rule)
                .containsExactly(DiscountRule.CATEGORY, DiscountRule.VOLUME, DiscountRule.COUPON);
        assertThat(resultado.discounts()).extracting(AppliedDiscount::rate).extracting(BigDecimal::toPlainString)
                .containsExactly("0.10", "0.05", "0.15");
        assertThat(resultado.maxDiscountRate()).isEqualTo(new BigDecimal("0.35"));
    }

    @Test
    void cambiarLaConfiguracionCambiaElResultadoSinTocarElCodigo() {
        DiscountEngine engine = DiscountEngineFactory.create(propiedades("0.30", "0.35"), antes(VENCE));

        assertThat(engine.apply(DOS_LAPTOPS, "WELCOME2026").capApplied()).isTrue();
    }

    @Test
    void elVencimientoDelCuponSaleDeLaConfiguracion() {
        DiscountEngine engine = DiscountEngineFactory.create(propiedades("0.10", "0.35"),
                Clock.fixed(VENCE, ZoneOffset.UTC));

        assertThatThrownBy(() -> engine.apply(DOS_LAPTOPS, "WELCOME2026"))
                .isInstanceOf(CouponExpiredException.class);
    }

    private static Clock antes(Instant instante) {
        return Clock.fixed(instante.minusSeconds(60), ZoneOffset.UTC);
    }

    private static DiscountProperties propiedades(String tasaCategoria, String tope) {
        return new DiscountProperties(
                new DiscountProperties.Category("Tecnología", new BigDecimal(tasaCategoria)),
                new DiscountProperties.Volume(new BigDecimal("100.00"), new BigDecimal("0.05")),
                new DiscountProperties.Coupon("WELCOME2026", new BigDecimal("0.15"), VENCE),
                new BigDecimal(tope));
    }
}
