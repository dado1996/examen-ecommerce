package com.kata.checkout.api.dto;

import com.kata.checkout.application.CheckoutCommand;
import com.kata.checkout.domain.model.CartItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Cuerpo de {@code POST /checkouts} y {@code /checkouts/preview}. Es cerrado: Jackson rechaza
 * cualquier otra propiedad ({@code spring.jackson.deserialization.fail-on-unknown-properties}).
 * Una lista vacía no se valida aquí: el dominio la rechaza como {@code EMPTY_CART}.
 */
public record CheckoutRequest(
        @NotNull(message = "es obligatorio") List<@NotNull(message = "no puede ser nulo") @Valid CheckoutItemRequest> items,
        @Size(min = 1, max = 50, message = "debe tener entre 1 y 50 caracteres") String couponCode) {

    public CheckoutCommand toCommand() {
        return new CheckoutCommand(
                items.stream().map(i -> new CartItem(i.productId(), i.quantity())).toList(),
                couponCode);
    }
}
