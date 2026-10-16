package com.kata.checkout.application;

import com.kata.checkout.domain.discount.DiscountEngine;
import com.kata.checkout.domain.exception.IdempotencyKeyReusedException;
import com.kata.checkout.domain.exception.InsufficientStockException;
import com.kata.checkout.domain.exception.ProductNotFoundException;
import com.kata.checkout.domain.model.Cart;
import com.kata.checkout.domain.model.CheckoutBreakdown;
import com.kata.checkout.domain.model.Idempotency;
import com.kata.checkout.domain.model.PricedLine;
import com.kata.checkout.domain.model.SavedOrder;
import com.kata.checkout.domain.model.StoredCheckout;
import com.kata.checkout.domain.port.OrderRepository;
import com.kata.checkout.domain.port.ProductCatalog;
import com.kata.checkout.domain.port.StockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Casos de uso del checkout (HU2, HU3, HU4). Las validaciones van en este orden: carrito vacío,
 * producto inexistente, cupón y stock. Así un pedido inválido nunca llega a reservar unidades.
 */
public class CheckoutService {

    private final ProductCatalog catalog;
    private final StockRepository stock;
    private final OrderRepository orders;
    private final DiscountEngine engine;
    private final UnitOfWork transactions;

    public CheckoutService(ProductCatalog catalog, StockRepository stock, OrderRepository orders,
                           DiscountEngine engine, UnitOfWork transactions) {
        this.catalog = catalog;
        this.stock = stock;
        this.orders = orders;
        this.engine = engine;
        this.transactions = transactions;
    }

    /** Calcula el desglose con las mismas validaciones del checkout, pero no reserva ni guarda. */
    public CheckoutBreakdown preview(CheckoutCommand command) {
        CheckoutBreakdown breakdown = price(command);
        for (PricedLine line : breakdown.lines()) {
            int available = stock.available(line.product().id());
            if (available < line.quantity()) {
                throw new InsufficientStockException(line.product().id(), line.quantity(), available);
            }
        }
        return breakdown;
    }

    /** Checkout sin clave de idempotencia: cada llamada crea una orden nueva. */
    public ConfirmedCheckout checkout(CheckoutCommand command) {
        return checkout(command, null);
    }

    /**
     * Reserva el stock de todas las líneas y guarda la orden. Si una reserva, el guardado o la
     * confirmación de la transacción fallan, devuelve lo reservado: el stock queda como estaba y no
     * se guarda nada.
     *
     * <p>Con clave de idempotencia, la búsqueda de la clave y el guardado van en la misma
     * transacción. Si la clave ya se usó con el mismo pedido, devuelve esa orden sin validar ni
     * reservar otra vez (aunque el cupón haya vencido o el stock se haya agotado desde entonces).
     * Un intento que falló no guardó la clave, así que se puede reintentar con ella.
     *
     * @param idempotencyKey UUID canónico en minúsculas, o {@code null}
     * @throws IdempotencyKeyReusedException si la clave ya se usó con otro pedido
     */
    public ConfirmedCheckout checkout(CheckoutCommand command, String idempotencyKey) {
        Idempotency idempotency = idempotencyKey == null ? null : new Idempotency(idempotencyKey, command.fingerprint());
        List<PricedLine> reserved = new ArrayList<>();
        try {
            return transactions.run(() -> {
                Optional<ConfirmedCheckout> previous = previous(idempotency);
                if (previous.isPresent()) {
                    return previous.get();
                }
                CheckoutBreakdown breakdown = price(command);
                for (PricedLine line : breakdown.lines()) {
                    stock.reserve(line.product().id(), line.quantity());
                    reserved.add(line);
                }
                SavedOrder saved = orders.save(breakdown.toOrder(), idempotency);
                return new ConfirmedCheckout(saved.id(), saved.createdAt(), breakdown);
            });
        } catch (RuntimeException | Error e) {
            release(reserved, e);
            throw e;
        }
    }

    private Optional<ConfirmedCheckout> previous(Idempotency idempotency) {
        if (idempotency == null) {
            return Optional.empty();
        }
        Optional<StoredCheckout> stored = orders.findByIdempotencyKey(idempotency.key());
        if (stored.isPresent() && !stored.get().requestHash().equals(idempotency.requestHash())) {
            throw new IdempotencyKeyReusedException();
        }
        return stored.map(s -> new ConfirmedCheckout(s.id(), s.createdAt(), s.breakdown()));
    }

    private void release(List<PricedLine> reserved, Throwable cause) {
        for (PricedLine line : reserved) {
            try {
                stock.release(line.product().id(), line.quantity());
            } catch (RuntimeException releaseFailure) {
                cause.addSuppressed(releaseFailure);
            }
        }
    }

    private CheckoutBreakdown price(CheckoutCommand command) {
        Cart cart = Cart.of(command.items());
        List<PricedLine> lines = cart.items().stream()
                .map(item -> new PricedLine(
                        catalog.findActiveById(item.productId())
                                .orElseThrow(() -> new ProductNotFoundException(item.productId())),
                        item.quantity()))
                .toList();
        return new CheckoutBreakdown(lines, engine.apply(lines, command.couponCode()));
    }
}
