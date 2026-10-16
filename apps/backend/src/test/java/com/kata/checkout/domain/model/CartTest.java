package com.kata.checkout.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.exception.EmptyCartException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartTest {

    @Test
    void unCarritoSinProductosSeRechazaComoVacio() {
        assertThatThrownBy(() -> Cart.of(List.of())).isInstanceOf(EmptyCartException.class);
        assertThatThrownBy(() -> Cart.of(null)).isInstanceOf(EmptyCartException.class);
    }

    @Test
    void consolidaLasCantidadesDeUnProductoRepetidoConservandoElOrdenDeAparicion() {
        Cart cart = Cart.of(List.of(new CartItem(4, 1), new CartItem(1, 2), new CartItem(4, 3)));

        assertThat(cart.items()).containsExactly(new CartItem(4, 4), new CartItem(1, 2));
    }

    @Test
    void unaSumaQueDesbordaElEnteroQuedaEnElMaximoParaQueFalleComoStockInsuficiente() {
        Cart cart = Cart.of(List.of(new CartItem(1, Integer.MAX_VALUE), new CartItem(1, 5)));

        assertThat(cart.items()).containsExactly(new CartItem(1, Integer.MAX_VALUE));
    }

    @Test
    void rechazaUnElementoNulo() {
        List<CartItem> conNulo = new ArrayList<>();
        conNulo.add(null);

        assertThatThrownBy(() -> Cart.of(conNulo)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void unaLineaDelCarritoExigeProductoYCantidadPositivos() {
        assertThatThrownBy(() -> new CartItem(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartItem(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elSubtotalDeUnaLineaEsElPrecioDelCatalogoPorLaCantidad() {
        Product cuaderno = new Product(5, "Cuaderno", "Papelería", new BigDecimal("4.50"));

        assertThat(new PricedLine(cuaderno, 3).lineSubtotal()).isEqualByComparingTo("13.50");
        assertThatThrownBy(() -> new PricedLine(cuaderno, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unProductoExigeNombreCategoriaYPrecioPositivo() {
        BigDecimal precio = new BigDecimal("1.00");
        assertThatThrownBy(() -> new Product(1, " ", "Hogar", precio)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Product(1, "A", " ", precio)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Product(1, "A", "Hogar", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
