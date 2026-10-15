package com.kata.checkout.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros de las reglas de descuento, leídos de {@code application.yml}.
 * Se validan al arrancar: una tasa fuera de [0, 1] impide iniciar la aplicación.
 */
@Validated
@ConfigurationProperties(prefix = "discounts")
public record DiscountProperties(
        @Valid @NotNull Category category,
        @Valid @NotNull Volume volume,
        @Valid @NotNull Coupon coupon,
        @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal maxTotalRate) {

    public record Category(
            @NotBlank String name,
            @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal rate) {
    }

    public record Volume(
            @NotNull @DecimalMin("0.00") BigDecimal threshold,
            @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal rate) {
    }

    public record Coupon(
            @NotBlank String code,
            @NotNull @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal rate) {
    }
}
