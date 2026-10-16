package com.kata.checkout.application;

import com.kata.checkout.domain.model.Product;

/** Producto del catálogo con su stock vivo. */
public record ProductStock(Product product, int availableStock) {
}
