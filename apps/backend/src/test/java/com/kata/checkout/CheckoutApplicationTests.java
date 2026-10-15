package com.kata.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.kata.checkout.infrastructure.config.DiscountProperties;
import java.math.BigDecimal;
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
        assertThat(discounts.maxTotalRate()).isEqualByComparingTo(new BigDecimal("0.35"));
    }
}
