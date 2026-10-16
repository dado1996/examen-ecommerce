package com.kata.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.discount.CategoryDiscount;
import com.kata.checkout.domain.discount.CouponDiscount;
import com.kata.checkout.domain.discount.DiscountEngine;
import com.kata.checkout.domain.discount.VolumeDiscount;
import com.kata.checkout.domain.exception.CouponExpiredException;
import com.kata.checkout.domain.exception.EmptyCartException;
import com.kata.checkout.domain.exception.IdempotencyKeyReusedException;
import com.kata.checkout.domain.exception.InsufficientStockException;
import com.kata.checkout.domain.exception.InvalidCouponException;
import com.kata.checkout.domain.exception.ProductNotFoundException;
import com.kata.checkout.domain.model.CartItem;
import com.kata.checkout.domain.discount.DiscountResult;
import com.kata.checkout.domain.model.CheckoutBreakdown;
import com.kata.checkout.domain.model.Idempotency;
import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.PricedLine;
import com.kata.checkout.domain.model.Product;
import com.kata.checkout.domain.model.SavedOrder;
import com.kata.checkout.domain.model.StoredCheckout;
import com.kata.checkout.domain.port.OrderRepository;
import com.kata.checkout.domain.port.ProductCatalog;
import com.kata.checkout.infrastructure.stock.InMemoryStockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T19:30:00Z");
    private static final Product LAPTOP = new Product(1, "Laptop 14\"", "Tecnología", new BigDecimal("1200.00"));
    private static final Product LAMPARA = new Product(4, "Lámpara de escritorio", "Hogar", new BigDecimal("40.00"));

    private static final String CLAVE = "3f0c9d2e-7b1a-4c5e-9f8d-2a6b4e1c0d7f";

    private final InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 10, 4L, 1));
    private final TransaccionesDePrueba transacciones = new TransaccionesDePrueba();
    private final OrdenesEnMemoria orders = new OrdenesEnMemoria(transacciones);
    private final CheckoutService service = servicio(Instant.parse("2027-01-01T00:00:00Z"));

    @Test
    void laPrevisualizacionCalculaElDesgloseSinGuardarNiReservarStock() {
        CheckoutBreakdown desglose = service.preview(pedido("WELCOME2026", new CartItem(1, 2), new CartItem(4, 1)));

        assertThat(desglose.result().total()).isEqualTo(new BigDecimal("1776.50"));
        assertThat(desglose.lines()).extracting(l -> l.product().name())
                .containsExactly("Laptop 14\"", "Lámpara de escritorio");
        assertThat(orders.guardadas).isEmpty();
        assertThat(stock.available(1)).isEqualTo(10);
        assertThat(stock.available(4)).isEqualTo(1);
    }

    @Test
    void elCheckoutReservaElStockGuardaLaOrdenYDevuelveSuIdentificador() {
        ConfirmedCheckout confirmado = service.checkout(pedido("WELCOME2026", new CartItem(1, 2), new CartItem(4, 1)));

        assertThat(confirmado.id()).isEqualTo(1);
        assertThat(confirmado.createdAt()).isEqualTo(AHORA);
        assertThat(confirmado.breakdown().result().discountTotal()).isEqualTo(new BigDecimal("663.50"));
        assertThat(stock.available(1)).isEqualTo(8);
        assertThat(stock.available(4)).isZero();
        Order guardada = orders.guardadas.get(0);
        assertThat(guardada.userId()).isNull();
        assertThat(guardada.couponCode()).isEqualTo("WELCOME2026");
        assertThat(guardada.total()).isEqualTo(new BigDecimal("1776.50"));
        assertThat(guardada.lines()).extracting(l -> l.unitPrice()).containsExactly(
                new BigDecimal("1200.00"), new BigDecimal("40.00"));
    }

    @Test
    void elPrecioSiempreSaleDelCatalogo() {
        CheckoutBreakdown desglose = service.preview(pedido(null, new CartItem(4, 1)));

        assertThat(desglose.lines().get(0).product().unitPrice()).isEqualTo(new BigDecimal("40.00"));
        assertThat(desglose.result().subtotal()).isEqualTo(new BigDecimal("40.00"));
    }

    @Test
    void lasCantidadesDeUnProductoRepetidoSeSumanAntesDeValidarElStock() {
        CheckoutBreakdown desglose = service.preview(pedido(null, new CartItem(1, 4), new CartItem(1, 6)));

        assertThat(desglose.lines()).singleElement().satisfies(l -> assertThat(l.quantity()).isEqualTo(10));
        assertThatThrownBy(() -> service.preview(pedido(null, new CartItem(1, 4), new CartItem(1, 7))))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void rechazaUnCarritoVacioEnLaPrevisualizacionYEnElCheckout() {
        assertThatThrownBy(() -> service.preview(pedido(null))).isInstanceOf(EmptyCartException.class);
        assertThatThrownBy(() -> service.checkout(pedido(null))).isInstanceOf(EmptyCartException.class);
        assertThat(orders.guardadas).isEmpty();
    }

    @Test
    void rechazaUnProductoQueNoEstaEnElCatalogoSinReservarNada() {
        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(1, 1), new CartItem(999, 1))))
                .isInstanceOf(ProductNotFoundException.class)
                .satisfies(e -> assertThat(((ProductNotFoundException) e).productId()).isEqualTo(999));
        assertThat(stock.available(1)).isEqualTo(10);
        assertThat(orders.guardadas).isEmpty();
    }

    @Test
    void rechazaUnCuponNoRegistradoSinReservarNiGuardar() {
        assertThatThrownBy(() -> service.checkout(pedido("BIENVENIDO", new CartItem(1, 1))))
                .isInstanceOf(InvalidCouponException.class);
        assertThat(stock.available(1)).isEqualTo(10);
        assertThat(orders.guardadas).isEmpty();
    }

    @Test
    void rechazaUnCuponExpiradoSinReservarNiGuardar() {
        CheckoutService conCuponVencido = servicio(AHORA.minusSeconds(1));

        assertThatThrownBy(() -> conCuponVencido.checkout(pedido("WELCOME2026", new CartItem(1, 1))))
                .isInstanceOf(CouponExpiredException.class);
        assertThat(stock.available(1)).isEqualTo(10);
        assertThat(orders.guardadas).isEmpty();
    }

    @Test
    void laPrevisualizacionRechazaUnaCantidadMayorQueElStockDisponible() {
        assertThatThrownBy(() -> service.preview(pedido(null, new CartItem(4, 2))))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(e -> {
                    InsufficientStockException ex = (InsufficientStockException) e;
                    assertThat(ex.productId()).isEqualTo(4);
                    assertThat(ex.requested()).isEqualTo(2);
                    assertThat(ex.available()).isEqualTo(1);
                });
    }

    @Test
    void siUnaLineaNoTieneStockDevuelveLoQueYaSeHabiaReservadoYNoGuarda() {
        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(1, 3), new CartItem(4, 2))))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(stock.available(1)).isEqualTo(10);
        assertThat(stock.available(4)).isEqualTo(1);
        assertThat(orders.guardadas).isEmpty();
    }

    @Test
    void siFallaElGuardadoDevuelveElStockYPropagaElError() {
        orders.falla = new IllegalStateException("base caída");

        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(1, 3), new CartItem(4, 1))))
                .isSameAs(orders.falla);
        assertThat(stock.available(1)).isEqualTo(10);
        assertThat(stock.available(4)).isEqualTo(1);
    }

    @Test
    void devuelveElStockAunqueElGuardadoFalleConUnError() {
        orders.falla = null;
        orders.error = new StackOverflowError();

        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(1, 3)))).isSameAs(orders.error);
        assertThat(stock.available(1)).isEqualTo(10);
    }

    @Test
    void unSegundoCheckoutVeElStockQueDejoElPrimero() {
        service.checkout(pedido(null, new CartItem(4, 1)));

        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(4, 1))))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(orders.guardadas).hasSize(1);
    }

    @Test
    void sinClaveDeIdempotenciaCadaCheckoutCreaUnaOrdenNueva() {
        ConfirmedCheckout primero = service.checkout(pedido(null, new CartItem(1, 1)));
        ConfirmedCheckout segundo = service.checkout(pedido(null, new CartItem(1, 1)));

        assertThat(segundo.id()).isNotEqualTo(primero.id());
        assertThat(stock.available(1)).isEqualTo(8);
        assertThat(orders.idempotencias).containsExactly(null, null);
    }

    @Test
    void guardaLaClaveDeIdempotenciaConElHashDelPedido() {
        CheckoutCommand pedido = pedido("WELCOME2026", new CartItem(1, 2));

        service.checkout(pedido, CLAVE);

        assertThat(orders.idempotencias).containsExactly(new Idempotency(CLAVE, pedido.fingerprint()));
    }

    @Test
    void unReintentoConLaMismaClaveYElMismoPedidoDevuelveLaOrdenYaCreadaSinReservarOtraVez() {
        ConfirmedCheckout primero = service.checkout(pedido("WELCOME2026", new CartItem(1, 2), new CartItem(4, 1)),
                CLAVE);

        // La lámpara ya no tiene stock: el reintento no vuelve a validar ni a reservar
        ConfirmedCheckout reintento = service.checkout(
                pedido("WELCOME2026", new CartItem(1, 2), new CartItem(4, 1)), CLAVE);

        assertThat(reintento.id()).isEqualTo(primero.id());
        assertThat(reintento.createdAt()).isEqualTo(primero.createdAt());
        assertThat(reintento.breakdown().result().total()).isEqualTo(new BigDecimal("1776.50"));
        assertThat(stock.available(1)).isEqualTo(8);
        assertThat(stock.available(4)).isZero();
        assertThat(orders.guardadas).hasSize(1);
    }

    @Test
    void elMismoCarritoEnOtroOrdenOConLineasRepetidasEsElMismoPedido() {
        ConfirmedCheckout primero = service.checkout(pedido(null, new CartItem(1, 2), new CartItem(4, 1)), CLAVE);

        ConfirmedCheckout reintento = service.checkout(
                pedido(null, new CartItem(4, 1), new CartItem(1, 1), new CartItem(1, 1)), CLAVE);

        assertThat(reintento.id()).isEqualTo(primero.id());
        assertThat(orders.guardadas).hasSize(1);
    }

    @Test
    void elReintentoDevuelveLaOrdenAunqueElCuponHayaVencidoDespues() {
        ConfirmedCheckout primero = service.checkout(pedido("WELCOME2026", new CartItem(1, 1)), CLAVE);
        CheckoutService conCuponVencido = servicio(AHORA);

        ConfirmedCheckout reintento = conCuponVencido.checkout(pedido("WELCOME2026", new CartItem(1, 1)), CLAVE);

        assertThat(reintento.id()).isEqualTo(primero.id());
    }

    @Test
    void laMismaClaveConOtroPedidoSeRechazaSinReservarNiGuardar() {
        service.checkout(pedido(null, new CartItem(1, 2)), CLAVE);

        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(1, 3)), CLAVE))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThatThrownBy(() -> service.checkout(pedido("WELCOME2026", new CartItem(1, 2)), CLAVE))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(stock.available(1)).isEqualTo(8);
        assertThat(orders.guardadas).hasSize(1);
    }

    @Test
    void unIntentoFallidoNoConsumeLaClave() {
        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(4, 2)), CLAVE))
                .isInstanceOf(InsufficientStockException.class);

        ConfirmedCheckout confirmado = service.checkout(pedido(null, new CartItem(4, 1)), CLAVE);

        assertThat(confirmado.id()).isEqualTo(1);
        assertThat(stock.available(4)).isZero();
    }

    @Test
    void buscaLaClaveYGuardaLaOrdenDentroDeUnaMismaTransaccion() {
        service.checkout(pedido(null, new CartItem(1, 1)), CLAVE);
        service.checkout(pedido(null, new CartItem(1, 1)), CLAVE);

        assertThat(orders.operacionesFueraDeTransaccion).isZero();
        assertThat(transacciones.ejecutadas).isEqualTo(2);
    }

    @Test
    void siFallaLaConfirmacionDeLaTransaccionDevuelveElStock() {
        transacciones.fallaAlConfirmar = new IllegalStateException("commit fallido");

        assertThatThrownBy(() -> service.checkout(pedido(null, new CartItem(1, 3)), CLAVE))
                .isSameAs(transacciones.fallaAlConfirmar);
        assertThat(stock.available(1)).isEqualTo(10);
    }

    private CheckoutService servicio(Instant venceElCupon) {
        DiscountEngine engine = new DiscountEngine(List.of(
                new CategoryDiscount("Tecnología", new BigDecimal("0.10")),
                new VolumeDiscount(new BigDecimal("100.00"), new BigDecimal("0.05")),
                new CouponDiscount("WELCOME2026", new BigDecimal("0.15"), venceElCupon,
                        Clock.fixed(AHORA, ZoneOffset.UTC))),
                new BigDecimal("0.35"));
        return new CheckoutService(new CatalogoFijo(LAPTOP, LAMPARA), stock, orders, engine, transacciones);
    }

    private static CheckoutCommand pedido(String cupon, CartItem... items) {
        return new CheckoutCommand(List.of(items), cupon);
    }

    static final class CatalogoFijo implements ProductCatalog {

        private final List<Product> products;

        CatalogoFijo(Product... products) {
            this.products = List.of(products);
        }

        @Override
        public List<Product> findAllActive() {
            return products;
        }

        @Override
        public Optional<Product> findActiveById(long id) {
            return products.stream().filter(p -> p.id() == id).findFirst();
        }
    }

    /** Ejecuta el trabajo en línea; puede simular que la confirmación (commit) falla. */
    static final class TransaccionesDePrueba implements UnitOfWork {

        boolean activa;
        int ejecutadas;
        RuntimeException fallaAlConfirmar;

        @Override
        public <T> T run(Supplier<T> work) {
            activa = true;
            try {
                T result = work.get();
                ejecutadas++;
                if (fallaAlConfirmar != null) {
                    throw fallaAlConfirmar;
                }
                return result;
            } finally {
                activa = false;
            }
        }
    }

    static final class OrdenesEnMemoria implements OrderRepository {

        final List<Order> guardadas = new ArrayList<>();
        final List<Idempotency> idempotencias = new ArrayList<>();
        final Map<String, StoredCheckout> porClave = new HashMap<>();
        private final TransaccionesDePrueba transacciones;
        int operacionesFueraDeTransaccion;
        RuntimeException falla;
        Error error;

        OrdenesEnMemoria(TransaccionesDePrueba transacciones) {
            this.transacciones = transacciones;
        }

        @Override
        public SavedOrder save(Order order, Idempotency idempotency) {
            registrarSiEstaFueraDeTransaccion();
            if (falla != null) {
                throw falla;
            }
            if (error != null) {
                throw error;
            }
            guardadas.add(order);
            idempotencias.add(idempotency);
            SavedOrder saved = new SavedOrder(guardadas.size(), AHORA);
            if (idempotency != null) {
                porClave.put(idempotency.key(),
                        new StoredCheckout(saved.id(), saved.createdAt(), idempotency.requestHash(), desglose(order)));
            }
            return saved;
        }

        @Override
        public Optional<StoredCheckout> findByIdempotencyKey(String key) {
            registrarSiEstaFueraDeTransaccion();
            return Optional.ofNullable(porClave.get(key));
        }

        private void registrarSiEstaFueraDeTransaccion() {
            if (!transacciones.activa) {
                operacionesFueraDeTransaccion++;
            }
        }

        private static CheckoutBreakdown desglose(Order order) {
            return new CheckoutBreakdown(
                    order.lines().stream()
                            .map(l -> new PricedLine(
                                    new Product(l.productId(), "Guardado", l.category(), l.unitPrice()), l.quantity()))
                            .toList(),
                    new DiscountResult(order.discounts(), order.subtotal(), order.discountTotal(), order.total(),
                            order.maxDiscountRate()));
        }

        @Override
        public Optional<Order> findById(long id) {
            return Optional.ofNullable(id <= guardadas.size() ? guardadas.get((int) id - 1) : null);
        }
    }
}
