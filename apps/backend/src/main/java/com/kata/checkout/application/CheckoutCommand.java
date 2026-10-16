package com.kata.checkout.application;

import com.kata.checkout.domain.model.CartItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lo que envía el cliente: productos, cantidades y cupón. Nunca precios.
 *
 * @param couponCode cupón opcional, o {@code null}
 */
public record CheckoutCommand(List<CartItem> items, String couponCode) {

    /**
     * SHA-256 hexadecimal del pedido normalizado: las cantidades de un producto repetido se suman y
     * las líneas se ordenan por producto, así que el orden del carrito no cambia el hash. El cupón
     * entra tal como llegó.
     */
    public String fingerprint() {
        Map<Long, Long> quantities = new TreeMap<>();
        for (CartItem item : items) {
            quantities.merge(item.productId(), (long) item.quantity(), Long::sum);
        }
        StringBuilder canonical = new StringBuilder();
        quantities.forEach((productId, quantity) -> canonical.append(productId).append('x').append(quantity).append(';'));
        canonical.append(couponCode == null ? "sin-cupon" : "cupon:" + couponCode);
        return HexFormat.of().formatHex(sha256().digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM está obligada a incluir SHA-256
            throw new IllegalStateException(e);
        }
    }
}
