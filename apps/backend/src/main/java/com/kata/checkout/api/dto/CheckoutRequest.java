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
 *
 * <p>{@code items} admite como máximo {@value #MAX_ITEMS} líneas: el backend busca cada una en el
 * catálogo, así que el tamaño acota el trabajo por petición. Un carrito real tiene pocas líneas
 * distintas (las unidades van en {@code quantity}), y 100 queda muy por encima sin dejar abusar.
 */
public record CheckoutRequest(
        @NotNull(message = "es obligatorio")
        @Size(max = CheckoutRequest.MAX_ITEMS, message = "debe tener como máximo 100 productos")
        List<@NotNull(message = "no puede ser nulo") @Valid CheckoutItemRequest> items,
        @Size(min = 1, max = 50, message = "debe tener entre 1 y 50 caracteres") String couponCode) {

    /** Líneas máximas por pedido; coincide con {@code maxItems} en el contrato. */
    public static final int MAX_ITEMS = 100;

    public CheckoutCommand toCommand() {
        return new CheckoutCommand(
                items.stream().map(i -> new CartItem(i.productId(), i.quantity())).toList(),
                couponCode);
    }
}
