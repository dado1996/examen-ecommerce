package com.kata.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kata.checkout.domain.model.Product;
import com.kata.checkout.infrastructure.stock.InMemoryStockRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    private static final Product LAPTOP = new Product(1, "Laptop 14\"", "Tecnología", new BigDecimal("1200.00"));
    private static final Product LAMPARA = new Product(4, "Lámpara de escritorio", "Hogar", new BigDecimal("40.00"));
    private static final Product SILLA = new Product(6, "Silla ergonómica", "Hogar", new BigDecimal("310.00"));

    @Test
    void listaSoloLosProductosConStockVivoMayorQueCero() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 10, 4L, 15, 6L, 0));
        CatalogService service = new CatalogService(new CheckoutServiceTest.CatalogoFijo(LAPTOP, LAMPARA, SILLA), stock);

        assertThat(service.listInStock())
                .containsExactly(new ProductStock(LAPTOP, 10), new ProductStock(LAMPARA, 15));
    }

    @Test
    void elStockListadoEsElVivoYNoElInicial() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 10, 4L, 1));
        CatalogService service = new CatalogService(new CheckoutServiceTest.CatalogoFijo(LAPTOP, LAMPARA), stock);

        stock.reserve(1, 3);
        stock.reserve(4, 1);

        assertThat(service.listInStock()).containsExactly(new ProductStock(LAPTOP, 7));
    }
}
