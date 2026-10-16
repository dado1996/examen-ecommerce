package com.kata.checkout.domain.port;

import com.kata.checkout.domain.model.Product;
import java.util.List;
import java.util.Optional;

/** Catálogo de productos: la única fuente de precios. Solo expone productos activos. */
public interface ProductCatalog {

    /** Productos activos ordenados por {@code id}. */
    List<Product> findAllActive();

    /** El producto si existe y está activo. */
    Optional<Product> findActiveById(long id);
}
