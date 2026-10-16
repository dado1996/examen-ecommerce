package com.kata.checkout.domain.port;

import com.kata.checkout.domain.exception.InsufficientStockException;

/** Stock disponible por producto. */
public interface StockRepository {

    int available(long productId);

    /**
     * Descuenta la cantidad de forma atómica.
     *
     * @throws InsufficientStockException si no alcanza; en ese caso el stock no cambia
     */
    void reserve(long productId, int quantity);

    /** Devuelve al stock unidades reservadas antes, p. ej. si no se pudo guardar la orden. */
    void release(long productId, int quantity);
}
