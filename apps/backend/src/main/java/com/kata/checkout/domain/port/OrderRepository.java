package com.kata.checkout.domain.port;

import com.kata.checkout.domain.model.Idempotency;
import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.SavedOrder;
import com.kata.checkout.domain.model.StoredCheckout;
import java.util.Optional;

/** Persistencia de órdenes confirmadas. */
public interface OrderRepository {

    /** Guarda la orden sin clave de idempotencia. */
    default SavedOrder save(Order order) {
        return save(order, null);
    }

    /**
     * Guarda la orden completa de forma atómica y devuelve el identificador y la fecha asignados.
     *
     * @param idempotency clave y hash del pedido, o {@code null}; una clave repetida se rechaza
     */
    SavedOrder save(Order order, Idempotency idempotency);

    Optional<Order> findById(long id);

    /** El checkout guardado con esa clave, con su desglose tal como se respondió. */
    Optional<StoredCheckout> findByIdempotencyKey(String key);
}
