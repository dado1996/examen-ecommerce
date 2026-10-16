package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.exception.InsufficientStockException;
import com.kata.checkout.domain.port.StockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SiembraDeStockTest extends SqliteIntegrationTest {

    @Autowired
    private StockRepository stock;

    @Test
    void elStockEnMemoriaSeSiembraDesdeElInventarioInicialDelCatalogo() {
        assertThat(stock.available(1)).isEqualTo(10);
        assertThat(stock.available(5)).isEqualTo(100);
    }

    @Test
    void unProductoSinInventarioInicialQuedaSinStock() {
        assertThat(stock.available(6)).isZero();
    }

    @Test
    void unProductoFueraDelCatalogoNoTieneStock() {
        assertThat(stock.available(999)).isZero();
    }

    @Test
    void noSePuedeReservarUnProductoSembradoSinInventario() {
        assertThatThrownBy(() -> stock.reserve(6, 1)).isInstanceOf(InsufficientStockException.class);
        assertThat(stock.available(6)).isZero();
    }
}
