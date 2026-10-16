package com.kata.checkout.infrastructure.stock;

import com.kata.checkout.domain.exception.InsufficientStockException;
import com.kata.checkout.domain.port.StockRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stock en memoria (HU3). El decremento ocurre dentro de {@link ConcurrentHashMap#compute}, que es
 * atómico por producto: dos checkouts concurrentes no pueden vender la misma unidad.
 */
public class InMemoryStockRepository implements StockRepository {

    private final ConcurrentHashMap<Long, Integer> stock = new ConcurrentHashMap<>();

    public InMemoryStockRepository(Map<Long, Integer> initialStock) {
        initialStock.forEach((productId, quantity) -> {
            if (quantity < 0) {
                throw new IllegalArgumentException("Stock inicial negativo para el producto " + productId);
            }
            stock.put(productId, quantity);
        });
    }

    @Override
    public int available(long productId) {
        return stock.getOrDefault(productId, 0);
    }

    @Override
    public void reserve(long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero: " + quantity);
        }
        stock.compute(productId, (id, current) -> {
            int available = current == null ? 0 : current;
            if (available < quantity) {
                throw new InsufficientStockException(productId, quantity, available);
            }
            return available - quantity;
        });
    }
}
