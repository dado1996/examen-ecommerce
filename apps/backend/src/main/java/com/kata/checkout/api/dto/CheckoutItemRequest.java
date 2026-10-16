package com.kata.checkout.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Un producto del carrito. No tiene precio: el backend lo toma de su catálogo. */
public record CheckoutItemRequest(
        @NotNull(message = "es obligatorio") @Min(value = 1, message = "debe ser mayor o igual que 1") Long productId,
        @NotNull(message = "es obligatorio") @Min(value = 1, message = "debe ser mayor o igual que 1") Integer quantity) {
}
