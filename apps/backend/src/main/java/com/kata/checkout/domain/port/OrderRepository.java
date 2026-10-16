package com.kata.checkout.domain.port;

import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.SavedOrder;
import java.util.Optional;

/** Persistencia de órdenes confirmadas. */
public interface OrderRepository {

    /** Guarda la orden completa de forma atómica y devuelve el identificador y la fecha asignados. */
    SavedOrder save(Order order);

    Optional<Order> findById(long id);
}
