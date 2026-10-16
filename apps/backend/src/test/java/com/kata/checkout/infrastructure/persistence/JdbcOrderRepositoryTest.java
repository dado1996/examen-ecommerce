package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.OrderLine;
import com.kata.checkout.domain.port.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcOrderRepositoryTest extends SqliteIntegrationTest {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiarCheckouts() {
        // ON DELETE CASCADE borra también líneas y descuentos
        jdbc.update("DELETE FROM checkouts");
    }

    @Test
    void elEsquemaYLosDatosDeDemostracionSeCarganAlArrancar() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT category FROM products WHERE id = 1", String.class))
                .isEqualTo("Tecnología");
    }

    @Test
    void lasLlavesForaneasEstanActivasEnLaConexionDelPool() {
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
    }

    @Test
    void guardaCabeceraLineasYDescuentosEnUnaSolaOperacion() {
        long id = orders.save(ordenConCupon());

        Map<String, Object> cabecera = jdbc.queryForMap("SELECT * FROM checkouts WHERE id = ?", id);
        assertThat(cabecera)
                .containsEntry("user_id", 1)
                .containsEntry("coupon_code", "WELCOME2026")
                .containsEntry("subtotal", "2440.00")
                .containsEntry("discount_total", "663.50")
                .containsEntry("total", "1776.50")
                .containsEntry("max_discount_rate", "0.35");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM checkout_items WHERE checkout_id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForList(
                "SELECT rule FROM checkout_discounts WHERE checkout_id = ? ORDER BY step", String.class, id))
                .containsExactly("CATEGORY", "VOLUME", "COUPON");
    }

    @Test
    void guardaElDineroComoTextoDecimalExactoSinRedondearLosPasosIntermedios() {
        long id = orders.save(ordenConCupon());

        assertThat(jdbc.queryForObject(
                "SELECT typeof(base_amount) || ' ' || base_amount || ' ' || amount "
                        + "FROM checkout_discounts WHERE checkout_id = ? AND rule = 'COUPON'", String.class, id))
                .isEqualTo("text 2090.0000 313.500000");
        assertThat(jdbc.queryForObject(
                "SELECT line_subtotal FROM checkout_items WHERE checkout_id = ? AND product_id = 1", String.class, id))
                .isEqualTo("2400.00");
    }

    @Test
    void leeElCheckoutGuardadoConLosMismosMontosYEscalas() {
        Order original = ordenConCupon();
        long id = orders.save(original);

        assertThat(orders.findById(id)).contains(original);
    }

    @Test
    void guardaUnCheckoutDeInvitadoSinCuponNiDescuentos() {
        Order invitado = new Order(null, null,
                List.of(new OrderLine(5, 2, new BigDecimal("4.50"), "Papelería")),
                List.of(),
                new BigDecimal("9.00"), new BigDecimal("0.00"), new BigDecimal("9.00"), new BigDecimal("0.35"));

        long id = orders.save(invitado);

        assertThat(orders.findById(id)).contains(invitado);
        assertThat(jdbc.queryForObject("SELECT user_id IS NULL FROM checkouts WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void registraElPasoDeTopeCuandoElDescuentoSuperaElTreintaYCincoPorCiento() {
        // Tasas hipotéticas (categoría 30%) para que la cascada supere el tope: 30 + 10.5 = 40.5 > 35
        Order conTope = new Order(null, "WELCOME2026",
                List.of(new OrderLine(2, 4, new BigDecimal("25.00"), "Tecnología")),
                List.of(
                        new AppliedDiscount(DiscountRule.CATEGORY, new BigDecimal("0.30"), "Tecnología",
                                new BigDecimal("100.00"), new BigDecimal("30.0000")),
                        new AppliedDiscount(DiscountRule.COUPON, new BigDecimal("0.15"), "WELCOME2026",
                                new BigDecimal("70.0000"), new BigDecimal("10.500000")),
                        new AppliedDiscount(DiscountRule.CAP, new BigDecimal("0.35"), null,
                                new BigDecimal("40.500000"), new BigDecimal("5.500000"))),
                new BigDecimal("100.00"), new BigDecimal("35.00"), new BigDecimal("65.00"), new BigDecimal("0.35"));

        long id = orders.save(conTope);

        assertThat(jdbc.queryForObject("SELECT cap_applied FROM checkout_summary WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(orders.findById(id)).hasValueSatisfying(o -> assertThat(o.capApplied()).isTrue());
    }

    @Test
    void devuelveVacioSiElCheckoutNoExiste() {
        assertThat(orders.findById(999_999)).isEmpty();
    }

    @Test
    void rechazaUnaLineaConUnProductoInexistenteYNoDejaUnaOrdenAMedias() {
        Order productoInexistente = new Order(1L, null,
                List.of(new OrderLine(1, 1, new BigDecimal("1200.00"), "Tecnología"),
                        new OrderLine(999, 1, new BigDecimal("10.00"), "Hogar")),
                List.of(),
                new BigDecimal("1210.00"), new BigDecimal("0.00"), new BigDecimal("1210.00"), new BigDecimal("0.35"));

        assertThatThrownBy(() -> orders.save(productoInexistente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FOREIGN KEY");
        assertThat(contarFilas()).isZero();
    }

    @Test
    void rechazaUnUsuarioInexistentePorLlaveForanea() {
        Order usuarioInexistente = new Order(999L, null,
                List.of(new OrderLine(4, 1, new BigDecimal("40.00"), "Hogar")),
                List.of(),
                new BigDecimal("40.00"), new BigDecimal("0.00"), new BigDecimal("40.00"), new BigDecimal("0.35"));

        assertThatThrownBy(() -> orders.save(usuarioInexistente))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FOREIGN KEY");
        assertThat(contarFilas()).isZero();
    }

    @Test
    void revierteLaCabeceraYLasLineasSiFallaElRegistroDeUnDescuento() {
        // Una tasa mayor que 1 viola el CHECK de checkout_discounts, el último paso de la escritura
        Order descuentoCorrupto = new Order(null, null,
                List.of(new OrderLine(4, 1, new BigDecimal("40.00"), "Hogar")),
                List.of(new AppliedDiscount(DiscountRule.CATEGORY, new BigDecimal("1.50"), "Hogar",
                        new BigDecimal("40.00"), new BigDecimal("4.00"))),
                new BigDecimal("40.00"), new BigDecimal("4.00"), new BigDecimal("36.00"), new BigDecimal("0.35"));

        assertThatThrownBy(() -> orders.save(descuentoCorrupto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHECK");
        assertThat(contarFilas()).isZero();
    }

    @Test
    void rechazaUnSubtotalConMasDeDosDecimalesSinGuardarNada() {
        Order tresDecimales = new Order(null, null,
                List.of(new OrderLine(4, 1, new BigDecimal("40.00"), "Hogar")), List.of(),
                new BigDecimal("40.000"), new BigDecimal("0.00"), new BigDecimal("40.000"), new BigDecimal("0.35"));

        assertThatThrownBy(() -> orders.save(tresDecimales))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHECK");
        assertThat(contarFilas()).isZero();
    }

    @Test
    void rechazaUnDescuentoTotalQueSuperaElTopeGuardadoEnLaOrden() {
        Order sobreElTope = new Order(null, null,
                List.of(new OrderLine(4, 1, new BigDecimal("100.00"), "Hogar")), List.of(),
                new BigDecimal("100.00"), new BigDecimal("35.01"), new BigDecimal("64.99"), new BigDecimal("0.35"));

        assertThatThrownBy(() -> orders.save(sobreElTope))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CHECK");
        assertThat(contarFilas()).isZero();
    }

    @Test
    void aceptaUnDescuentoTotalExactamenteEnElTope() {
        Order enElTope = new Order(null, null,
                List.of(new OrderLine(4, 1, new BigDecimal("100.00"), "Hogar")), List.of(),
                new BigDecimal("100.00"), new BigDecimal("35.00"), new BigDecimal("65.00"), new BigDecimal("0.35"));

        assertThat(orders.findById(orders.save(enElTope))).contains(enElTope);
    }

    @Test
    void rechazaUnMontoDeDescuentoNegativo() {
        Order negativo = new Order(null, null,
                List.of(new OrderLine(4, 1, new BigDecimal("40.00"), "Hogar")),
                List.of(new AppliedDiscount(DiscountRule.VOLUME, new BigDecimal("0.05"), "100.00",
                        new BigDecimal("40.00"), new BigDecimal("-2.00"))),
                new BigDecimal("40.00"), new BigDecimal("0.00"), new BigDecimal("40.00"), new BigDecimal("0.35"));

        assertThatThrownBy(() -> orders.save(negativo)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(contarFilas()).isZero();
    }

    @Test
    void conservaMontosQueUnDoubleNoRepresentaria() {
        // 17 dígitos significativos en el subtotal y 19 decimales en el descuento: un double los perdería
        Order montosExtremos = new Order(null, null,
                List.of(new OrderLine(1, 1, new BigDecimal("12345678901234567.89"), "Tecnología")),
                List.of(new AppliedDiscount(DiscountRule.CATEGORY, new BigDecimal("0.1"), "Tecnología",
                        new BigDecimal("0.1234567890123456789"), new BigDecimal("0.0000000000000000001"))),
                new BigDecimal("12345678901234567.89"), new BigDecimal("0.00"),
                new BigDecimal("12345678901234567.89"), new BigDecimal("0.35"));

        long id = orders.save(montosExtremos);

        assertThat(orders.findById(id)).contains(montosExtremos);
        assertThat(jdbc.queryForObject("SELECT subtotal FROM checkouts WHERE id = ?", String.class, id))
                .isEqualTo("12345678901234567.89");
    }

    private int contarFilas() {
        return jdbc.queryForObject(
                "SELECT (SELECT count(*) FROM checkouts) + (SELECT count(*) FROM checkout_items)"
                        + " + (SELECT count(*) FROM checkout_discounts)", Integer.class);
    }

    /** Ejemplo de packages/schema/README.md: 2 laptops + 1 lámpara con WELCOME2026. */
    private static Order ordenConCupon() {
        return new Order(1L, "WELCOME2026",
                List.of(new OrderLine(1, 2, new BigDecimal("1200.00"), "Tecnología"),
                        new OrderLine(4, 1, new BigDecimal("40.00"), "Hogar")),
                List.of(
                        new AppliedDiscount(DiscountRule.CATEGORY, new BigDecimal("0.10"), "Tecnología",
                                new BigDecimal("2400.00"), new BigDecimal("240.0000")),
                        new AppliedDiscount(DiscountRule.VOLUME, new BigDecimal("0.05"), "100.00",
                                new BigDecimal("2200.0000"), new BigDecimal("110.000000")),
                        new AppliedDiscount(DiscountRule.COUPON, new BigDecimal("0.15"), "WELCOME2026",
                                new BigDecimal("2090.0000"), new BigDecimal("313.500000"))),
                new BigDecimal("2440.00"), new BigDecimal("663.50"), new BigDecimal("1776.50"),
                new BigDecimal("0.35"));
    }
}
