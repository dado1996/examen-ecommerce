package com.kata.checkout.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final BigDecimal TOPE = new BigDecimal("0.35");
    private static final OrderLine LAMPARA = new OrderLine(4, 1, new BigDecimal("40.00"), "Hogar");

    @Test
    void elSubtotalDeLineaEsElPrecioPorLaCantidad() {
        assertThat(new OrderLine(1, 3, new BigDecimal("89.90"), "Tecnología").lineSubtotal())
                .isEqualTo(new BigDecimal("269.70"));
    }

    @Test
    void rechazaUnaLineaConCantidadNoPositiva() {
        assertThatThrownBy(() -> new OrderLine(1, 0, new BigDecimal("10.00"), "Hogar"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnaLineaConPrecioNoPositivo() {
        assertThatThrownBy(() -> new OrderLine(1, 1, BigDecimal.ZERO, "Hogar"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnaLineaSinCategoria() {
        assertThatThrownBy(() -> new OrderLine(1, 1, BigDecimal.TEN, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnaOrdenConElCarritoVacio() {
        assertThatThrownBy(() -> orden(List.of(), List.of(), "40.00", "0.00", "40.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void rechazaUnaOrdenConElMismoProductoEnDosLineas() {
        assertThatThrownBy(() -> orden(List.of(LAMPARA, LAMPARA), List.of(), "80.00", "0.00", "80.00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consolidar");
    }

    @Test
    void rechazaUnaOrdenCuyoTotalNoEsElSubtotalMenosElDescuento() {
        assertThatThrownBy(() -> orden(List.of(LAMPARA), List.of(), "40.00", "0.00", "39.00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnDescuentoSinMontos() {
        assertThatThrownBy(() -> new AppliedDiscount(DiscountRule.CAP, TOPE, null, null, BigDecimal.ONE))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void indicaQueSeAplicoElTopeSoloSiExisteElPasoCap() {
        AppliedDiscount cap = new AppliedDiscount(DiscountRule.CAP, TOPE, null, BigDecimal.TEN, BigDecimal.ONE);

        assertThat(orden(List.of(LAMPARA), List.of(cap), "40.00", "0.00", "40.00").capApplied()).isTrue();
        assertThat(orden(List.of(LAMPARA), List.of(), "40.00", "0.00", "40.00").capApplied()).isFalse();
    }

    @Test
    void lasListasDeLaOrdenNoSePuedenModificarDesdeAfuera() {
        List<OrderLine> lineas = new ArrayList<>(List.of(LAMPARA));
        Order orden = orden(lineas, List.of(), "40.00", "0.00", "40.00");

        lineas.clear();

        assertThat(orden.lines()).hasSize(1);
        assertThatThrownBy(() -> orden.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cadaReglaConoceSuPasoEnLaCascada() {
        assertThat(DiscountRule.values()).extracting(DiscountRule::step).containsExactly(1, 2, 3, 4);
    }

    private static Order orden(List<OrderLine> lines, List<AppliedDiscount> discounts,
                               String subtotal, String discountTotal, String total) {
        return new Order(null, null, lines, discounts,
                new BigDecimal(subtotal), new BigDecimal(discountTotal), new BigDecimal(total), TOPE);
    }
}
