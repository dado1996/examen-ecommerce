package com.kata.checkout.application;

import com.kata.checkout.domain.discount.DiscountEngine;
import com.kata.checkout.domain.exception.InsufficientStockException;
import com.kata.checkout.domain.exception.ProductNotFoundException;
import com.kata.checkout.domain.model.Cart;
import com.kata.checkout.domain.model.CheckoutBreakdown;
import com.kata.checkout.domain.model.PricedLine;
import com.kata.checkout.domain.model.SavedOrder;
import com.kata.checkout.domain.port.OrderRepository;
import com.kata.checkout.domain.port.ProductCatalog;
import com.kata.checkout.domain.port.StockRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * Casos de uso del checkout (HU2, HU3, HU4). Las validaciones van en este orden: carrito vacío,
 * producto inexistente, cupón y stock. Así un pedido inválido nunca llega a reservar unidades.
 */
public class CheckoutService {

    private final ProductCatalog catalog;
    private final StockRepository stock;
    private final OrderRepository orders;
    private final DiscountEngine engine;

    public CheckoutService(ProductCatalog catalog, StockRepository stock, OrderRepository orders,
                           DiscountEngine engine) {
        this.catalog = catalog;
        this.stock = stock;
        this.orders = orders;
        this.engine = engine;
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

    /**
     * Reserva el stock de todas las líneas y guarda la orden. Si una reserva o el guardado fallan,
     * devuelve lo reservado: el stock queda como estaba y no se guarda nada.
     */
    public ConfirmedCheckout checkout(CheckoutCommand command) {
        CheckoutBreakdown breakdown = price(command);
        List<PricedLine> reserved = new ArrayList<>();
        try {
            for (PricedLine line : breakdown.lines()) {
                stock.reserve(line.product().id(), line.quantity());
                reserved.add(line);
            }
            SavedOrder saved = orders.save(breakdown.toOrder());
            return new ConfirmedCheckout(saved.id(), saved.createdAt(), breakdown);
        } catch (RuntimeException | Error e) {
            release(reserved, e);
            throw e;
        }
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
