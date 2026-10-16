package com.kata.checkout.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Con los valores actuales el tope del 35% no se alcanza (máximo 27,325%). Esta configuración
 * alternativa, con categoría al 40% y el cupón vencido, demuestra la alerta de HU4 y COUPON_EXPIRED.
 */
@TestPropertySource(properties = {
        "discounts.category.rate=0.40",
        "discounts.coupon.expires-at=2020-01-01T00:00:00Z"})
class ConfiguracionAlternativaApiTest extends ApiIntegrationTest {

    @Test
    void truncaElDescuentoExactamenteEnElTopeYLoInformaEnElDesglose() throws Exception {
        // 2400 → categoría 960 + volumen 72 = 1032 (43%) > 840 (35%): se recortan 192
        previsualizar("""
                {"items": [{"productId": 1, "quantity": 2}]}""")
                .andExpect(status().isOk())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.discounts[2].step").value(4))
                .andExpect(jsonPath("$.discounts[2].rule").value("CAP"))
                .andExpect(jsonPath("$.discounts[2].rate").value("0.35"))
                .andExpect(jsonPath("$.discounts[2].reference").isEmpty())
                .andExpect(jsonPath("$.discounts[2].baseAmount").value(matchesPattern("1032\\.0+")))
                .andExpect(jsonPath("$.discounts[2].amount").value(matchesPattern("192\\.0+")))
                .andExpect(jsonPath("$.discountTotal").value("840.00"))
                .andExpect(jsonPath("$.total").value("1560.00"))
                .andExpect(jsonPath("$.capApplied").value(true));
    }

    @Test
    void elCheckoutConTopeSeGuardaYCumpleElContrato() throws Exception {
        confirmar("""
                {"items": [{"productId": 1, "quantity": 1}]}""")
                .andExpect(status().isCreated())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.capApplied").value(true))
                .andExpect(jsonPath("$.discountTotal").value("420.00"));
    }

    @Test
    void unCuponVencidoSeRechazaConSuPropioCodigo() throws Exception {
        confirmar("""
                {"items": [{"productId": 5, "quantity": 1}], "couponCode": "WELCOME2026"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.code").value("COUPON_EXPIRED"));
    }
}
