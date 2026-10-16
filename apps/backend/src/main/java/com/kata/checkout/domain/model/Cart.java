package com.kata.checkout.domain.model;

import com.kata.checkout.domain.exception.EmptyCartException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Carrito con un producto por línea: las cantidades de un producto repetido se suman. */
public final class Cart {

    private final List<CartItem> items;

    private Cart(List<CartItem> items) {
        this.items = List.copyOf(items);
    }

    /**
     * @throws EmptyCartException si no hay productos
     */
    public static Cart of(List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            throw new EmptyCartException();
        }
        Map<Long, Long> quantities = new LinkedHashMap<>();
        for (CartItem item : items) {
            Objects.requireNonNull(item, "item");
            quantities.merge(item.productId(), (long) item.quantity(), Long::sum);
        }
        // Una suma que desborda int nunca tiene stock: se satura para que falle como stock insuficiente
        return new Cart(quantities.entrySet().stream()
                .map(e -> new CartItem(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE)))
                .toList());
    }

    public List<CartItem> items() {
        return items;
    }
}
