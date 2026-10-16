package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kata.checkout.application.CheckoutCommand;
import com.kata.checkout.application.CheckoutService;
import com.kata.checkout.application.ConfirmedCheckout;
import com.kata.checkout.domain.model.CartItem;
import com.kata.checkout.domain.port.StockRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** El checkout real (servicio, transacción y SQLite) frente a peticiones simultáneas con la misma clave. */
class IdempotenciaConcurrenteTest extends SqliteIntegrationTest {

    @Autowired
    private CheckoutService checkout;

    @Autowired
    private StockRepository stock;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dosPeticionesSimultaneasConLaMismaClaveCreanUnaSolaOrdenYReservanUnaSolaVez() throws Exception {
        String clave = UUID.randomUUID().toString();
        CheckoutCommand pedido = new CheckoutCommand(List.of(new CartItem(2, 1)), null);
        int stockAntes = stock.available(2);
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<ConfirmedCheckout>> resultados = List.of(
                    hilos.submit(() -> {
                        salida.await();
                        return checkout.checkout(pedido, clave);
                    }),
                    hilos.submit(() -> {
                        salida.await();
                        return checkout.checkout(pedido, clave);
                    }));
            salida.countDown();

            long primero = resultados.get(0).get(30, TimeUnit.SECONDS).id();
            long segundo = resultados.get(1).get(30, TimeUnit.SECONDS).id();

            assertThat(segundo).isEqualTo(primero);
        } finally {
            hilos.shutdownNow();
        }
        assertThat(stock.available(2)).isEqualTo(stockAntes - 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM checkouts WHERE idempotency_key = ?", Integer.class,
                clave)).isEqualTo(1);
    }
}
