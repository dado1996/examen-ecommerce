package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kata.checkout.domain.model.Product;
import com.kata.checkout.domain.port.ProductCatalog;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcProductCatalogTest extends SqliteIntegrationTest {

    @Autowired
    private ProductCatalog catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void borrarProductoInactivo() {
        jdbc.update("DELETE FROM products WHERE id = 100");
    }

    @Test
    void listaLosProductosActivosOrdenadosPorIdConElPrecioExactoDelCatalogo() {
        assertThat(catalog.findAllActive()).extracting(Product::id).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(catalog.findAllActive().get(2))
                .isEqualTo(new Product(3, "Teclado mecánico", "Tecnología", new BigDecimal("89.90")));
    }

    @Test
    void buscaUnProductoActivoPorSuIdentificador() {
        assertThat(catalog.findActiveById(1)).hasValueSatisfying(p -> {
            assertThat(p.name()).isEqualTo("Laptop 14\"");
            assertThat(p.unitPrice()).isEqualTo(new BigDecimal("1200.00"));
        });
    }

    @Test
    void unProductoInexistenteNoSeEncuentra() {
        assertThat(catalog.findActiveById(999)).isEmpty();
    }

    @Test
    void unProductoInactivoNoSeListaNiSeEncuentra() {
        jdbc.update("INSERT INTO products (id, name, category, unit_price, initial_stock, active) "
                + "VALUES (100, 'Descontinuado', 'Hogar', '9.99', 5, 0)");

        assertThat(catalog.findActiveById(100)).isEmpty();
        assertThat(catalog.findAllActive()).extracting(Product::id).doesNotContain(100L);
    }
}
