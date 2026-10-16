package com.kata.checkout.infrastructure.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.exception.InsufficientStockException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryStockRepositoryTest {

    @Test
    void reservarDescuentaLaCantidadDelStockDisponible() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 10));

        stock.reserve(1, 3);

        assertThat(stock.available(1)).isEqualTo(7);
    }

    @Test
    void reservarExactamenteElStockDisponibleLoDejaEnCero() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 2));

        stock.reserve(1, 2);

        assertThat(stock.available(1)).isZero();
    }

    @Test
    void rechazaUnaReservaConStockInsuficienteSinModificarElStock() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 2));

        assertThatThrownBy(() -> stock.reserve(1, 3))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(e -> {
                    InsufficientStockException ex = (InsufficientStockException) e;
                    assertThat(ex.productId()).isEqualTo(1);
                    assertThat(ex.requested()).isEqualTo(3);
                    assertThat(ex.available()).isEqualTo(2);
                });
        assertThat(stock.available(1)).isEqualTo(2);
    }

    @Test
    void rechazaReservarUnProductoQueNoEstaEnElCatalogo() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of());

        assertThatThrownBy(() -> stock.reserve(999, 1)).isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void rechazaUnaCantidadNoPositiva() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 5));

        assertThatThrownBy(() -> stock.reserve(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(stock.available(1)).isEqualTo(5);
    }

    @Test
    void liberarDevuelveLasUnidadesReservadasAlStock() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 5));
        stock.reserve(1, 3);

        stock.release(1, 3);

        assertThat(stock.available(1)).isEqualTo(5);
    }

    @Test
    void rechazaLiberarUnaCantidadNoPositiva() {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 5));

        assertThatThrownBy(() -> stock.release(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(stock.available(1)).isEqualTo(5);
    }

    @Test
    void rechazaSembrarUnStockNegativo() {
        assertThatThrownBy(() -> new InMemoryStockRepository(Map.of(1L, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lasReservasConcurrentesNuncaVendenMasDelStockDisponible() throws Exception {
        InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 50));
        AtomicInteger exitosas = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            IntStream.range(0, 200).forEach(i -> pool.submit(() -> {
                try {
                    stock.reserve(1, 1);
                    exitosas.incrementAndGet();
                } catch (InsufficientStockException ignorada) {
                    // esperado cuando se agota
                }
            }));
        }

        assertThat(exitosas).hasValue(50);
        assertThat(stock.available(1)).isZero();
    }
}
