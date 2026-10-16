package com.kata.checkout.domain.port;

import com.kata.checkout.domain.model.Order;
import java.util.Optional;

/** Persistencia de órdenes confirmadas. */
public interface OrderRepository {

    /** Guarda la orden completa de forma atómica y devuelve su identificador. */
    long save(Order order);

    Optional<Order> findById(long id);
}
