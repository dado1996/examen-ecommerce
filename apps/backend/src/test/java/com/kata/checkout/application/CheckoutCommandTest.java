package com.kata.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kata.checkout.domain.model.CartItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckoutCommandTest {

    @Test
    void elHashDelPedidoEsUnSha256HexadecimalEnMinusculas() {
        assertThat(pedido("WELCOME2026", new CartItem(1, 2)).fingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void elMismoPedidoTieneSiempreElMismoHash() {
        assertThat(pedido("WELCOME2026", new CartItem(1, 2), new CartItem(4, 1)).fingerprint())
                .isEqualTo(pedido("WELCOME2026", new CartItem(1, 2), new CartItem(4, 1)).fingerprint());
    }

    @Test
    void elOrdenDeLasLineasYLasLineasRepetidasNoCambianElHash() {
        assertThat(pedido(null, new CartItem(4, 1), new CartItem(1, 1), new CartItem(1, 1)).fingerprint())
                .isEqualTo(pedido(null, new CartItem(1, 2), new CartItem(4, 1)).fingerprint());
    }

    @Test
    void otraCantidadOtroProductoUOtroCuponCambianElHash() {
        String base = pedido("WELCOME2026", new CartItem(1, 2)).fingerprint();

        assertThat(pedido("WELCOME2026", new CartItem(1, 3)).fingerprint()).isNotEqualTo(base);
        assertThat(pedido("WELCOME2026", new CartItem(2, 2)).fingerprint()).isNotEqualTo(base);
        assertThat(pedido("OTRO", new CartItem(1, 2)).fingerprint()).isNotEqualTo(base);
        assertThat(pedido(null, new CartItem(1, 2)).fingerprint()).isNotEqualTo(base);
    }

    @Test
    void unPedidoSinCuponNoSeConfundeConUnCuponVacio() {
        assertThat(pedido(null, new CartItem(1, 2)).fingerprint())
                .isNotEqualTo(pedido("", new CartItem(1, 2)).fingerprint());
    }

    private static CheckoutCommand pedido(String cupon, CartItem... items) {
        return new CheckoutCommand(List.of(items), cupon);
    }
}
