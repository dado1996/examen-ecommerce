package com.kata.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.kata.checkout.infrastructure.config.DiscountProperties;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:testdb?mode=memory&cache=shared")
class CheckoutApplicationTests {

    @Autowired
    private DiscountProperties discounts;

    @Test
    void cargaLasReglasDeDescuentoDesdeLaConfiguracion() {
        assertThat(discounts.category().name()).isEqualTo("Tecnología");
        assertThat(discounts.category().rate()).isEqualByComparingTo("0.10");
        assertThat(discounts.volume().threshold()).isEqualByComparingTo("100.00");
        assertThat(discounts.volume().rate()).isEqualByComparingTo("0.05");
        assertThat(discounts.coupon().code()).isEqualTo("WELCOME2026");
        assertThat(discounts.coupon().rate()).isEqualByComparingTo("0.15");
        assertThat(discounts.coupon().expiresAt()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(discounts.maxTotalRate()).isEqualByComparingTo(new BigDecimal("0.35"));
    }

    @Test
    void lasTasasSeLeenComoDecimalesExactosSinPasarPorPuntoFlotante() {
        assertThat(discounts.category().rate().toPlainString()).isEqualTo("0.10");
        assertThat(discounts.volume().threshold().toPlainString()).isEqualTo("100.00");
        assertThat(discounts.maxTotalRate().toPlainString()).isEqualTo("0.35");
    }
}
