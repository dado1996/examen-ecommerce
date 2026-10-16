package com.kata.checkout.domain.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.model.DiscountRule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReglasDeDescuentoTest {

    private static final BigDecimal DIEZ_POR_CIENTO = new BigDecimal("0.10");

    @Test
    void cadaReglaDeclaraSuPosicionEnLaCascada() {
        assertThat(new CategoryDiscount("Tecnología", DIEZ_POR_CIENTO).rule()).isEqualTo(DiscountRule.CATEGORY);
        assertThat(new VolumeDiscount(BigDecimal.TEN, DIEZ_POR_CIENTO).rule()).isEqualTo(DiscountRule.VOLUME);
        assertThat(new CouponDiscount("X", DIEZ_POR_CIENTO, Instant.EPOCH, Clock.systemUTC()).rule())
                .isEqualTo(DiscountRule.COUPON);
    }

    @Test
    void rechazaUnaTasaMayorQueUnoOMenorQueCero() {
        assertThatThrownBy(() -> new CategoryDiscount("Tecnología", new BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VolumeDiscount(BigDecimal.TEN, new BigDecimal("-0.1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CouponDiscount("X", new BigDecimal("2"), Instant.EPOCH, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnaCategoriaOUnCuponSinNombre() {
        assertThatThrownBy(() -> new CategoryDiscount(" ", DIEZ_POR_CIENTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CouponDiscount("", DIEZ_POR_CIENTO, Instant.EPOCH, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnUmbralDeVolumenNegativo() {
        assertThatThrownBy(() -> new VolumeDiscount(new BigDecimal("-1"), DIEZ_POR_CIENTO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaParametrosNulos() {
        assertThatThrownBy(() -> new CategoryDiscount(null, DIEZ_POR_CIENTO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VolumeDiscount(null, DIEZ_POR_CIENTO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CouponDiscount("X", DIEZ_POR_CIENTO, null, Clock.systemUTC()))
                .isInstanceOf(NullPointerException.class);
    }
}
