package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class JdbcInitialStockSourceTest {

    @Test
    void unCatalogoVacioSiembraUnStockVacio() {
        try (SingleConnectionDataSource ds = baseEnMemoriaSoloConEsquema()) {
            assertThat(new JdbcInitialStockSource(new JdbcTemplate(ds)).load()).isEmpty();
        }
    }

    @Test
    void siembraLosProductosConInventarioCeroYOmiteLosInactivos() {
        try (SingleConnectionDataSource ds = baseEnMemoriaSoloConEsquema()) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.update("""
                    INSERT INTO products (id, name, category, unit_price, initial_stock, active) VALUES
                        (1, 'Agotado', 'Hogar', '10.00', 0, 1),
                        (2, 'Disponible', 'Hogar', '10.00', 7, 1),
                        (3, 'Descontinuado', 'Hogar', '10.00', 9, 0)""");

            assertThat(new JdbcInitialStockSource(jdbc).load())
                    .containsOnlyKeys(1L, 2L)
                    .containsEntry(1L, 0)
                    .containsEntry(2L, 7);
        }
    }

    private static SingleConnectionDataSource baseEnMemoriaSoloConEsquema() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(ds);
        return ds;
    }
}
