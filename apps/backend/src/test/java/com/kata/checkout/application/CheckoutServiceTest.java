package com.kata.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.discount.CategoryDiscount;
import com.kata.checkout.domain.discount.CouponDiscount;
import com.kata.checkout.domain.discount.DiscountEngine;
import com.kata.checkout.domain.discount.VolumeDiscount;
import com.kata.checkout.domain.exception.CouponExpiredException;
import com.kata.checkout.domain.exception.EmptyCartException;
import com.kata.checkout.domain.exception.InsufficientStockException;
import com.kata.checkout.domain.exception.InvalidCouponException;
import com.kata.checkout.domain.exception.ProductNotFoundException;
import com.kata.checkout.domain.model.CartItem;
import com.kata.checkout.domain.model.CheckoutBreakdown;
import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.Product;
import com.kata.checkout.domain.model.SavedOrder;
import com.kata.checkout.domain.port.OrderRepository;
import com.kata.checkout.domain.port.ProductCatalog;
import com.kata.checkout.infrastructure.stock.InMemoryStockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-15T19:30:00Z");
    private static final Product LAPTOP = new Product(1, "Laptop 14\"", "Tecnología", new BigDecimal("1200.00"));
    private static final Product LAMPARA = new Product(4, "Lámpara de escritorio", "Hogar", new BigDecimal("40.00"));

    private final InMemoryStockRepository stock = new InMemoryStockRepository(Map.of(1L, 10, 4L, 1));
    private final OrdenesEnMemoria orders = new OrdenesEnMemoria();
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

    private CheckoutService servicio(Instant venceElCupon) {
        DiscountEngine engine = new DiscountEngine(List.of(
                new CategoryDiscount("Tecnología", new BigDecimal("0.10")),
                new VolumeDiscount(new BigDecimal("100.00"), new BigDecimal("0.05")),
                new CouponDiscount("WELCOME2026", new BigDecimal("0.15"), venceElCupon,
                        Clock.fixed(AHORA, ZoneOffset.UTC))),
                new BigDecimal("0.35"));
        return new CheckoutService(new CatalogoFijo(LAPTOP, LAMPARA), stock, orders, engine);
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

    static final class OrdenesEnMemoria implements OrderRepository {

        final List<Order> guardadas = new ArrayList<>();
        RuntimeException falla;
        Error error;

        @Override
        public SavedOrder save(Order order) {
            if (falla != null) {
                throw falla;
            }
            if (error != null) {
                throw error;
            }
            guardadas.add(order);
            return new SavedOrder(guardadas.size(), AHORA);
        }

        @Override
        public Optional<Order> findById(long id) {
            return Optional.ofNullable(id <= guardadas.size() ? guardadas.get((int) id - 1) : null);
        }
    }
}
