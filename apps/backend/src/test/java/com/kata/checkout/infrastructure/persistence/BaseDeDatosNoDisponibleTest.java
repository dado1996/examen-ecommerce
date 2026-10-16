package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.CheckoutApplication;
import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.OrderLine;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * No hay fallback ante una base caída: los adaptadores propagan un {@link DataAccessException}
 * de Spring (nunca un {@link SQLException} crudo) y el arranque falla rápido.
 */
class BaseDeDatosNoDisponibleTest {

    private static final String URL_INALCANZABLE =
            "jdbc:sqlite:/ruta/que/no/existe/checkout.db?foreign_keys=true";

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(URL_INALCANZABLE));

    @Test
    void guardarUnCheckoutSinBaseDisponibleLanzaUnErrorDeAccesoADatosDeSpring() {
        Order orden = new Order(null, null,
                List.of(new OrderLine(4, 1, new BigDecimal("40.00"), "Hogar")), List.of(),
                new BigDecimal("40.00"), new BigDecimal("0.00"), new BigDecimal("40.00"), new BigDecimal("0.35"));

        assertThatThrownBy(() -> new JdbcOrderRepository(jdbc).save(orden))
                .isInstanceOf(CannotGetJdbcConnectionException.class)
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void leerUnCheckoutSinBaseDisponibleLanzaUnErrorDeAccesoADatosDeSpring() {
        assertThatThrownBy(() -> new JdbcOrderRepository(jdbc).findById(1))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void sembrarElStockSinBaseDisponibleLanzaUnErrorDeAccesoADatos() {
        assertThatThrownBy(() -> new JdbcInitialStockSource(jdbc).load())
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void laAplicacionNoArrancaSiLaBaseNoEstaDisponible() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(CheckoutApplication.class)
                .properties("APP_DB_PATH=/ruta/que/no/existe/checkout.db", "server.port=0");

        assertThatThrownBy(() -> app.run().close())
                .hasRootCauseInstanceOf(SQLException.class);
        assertThat(app.context()).isNull();
    }
}
