package com.kata.checkout.application;

import com.kata.checkout.domain.port.ProductCatalog;
import com.kata.checkout.domain.port.StockRepository;
import java.util.List;

/** Caso de uso del catálogo (HU1). */
public class CatalogService {

    private final ProductCatalog catalog;
    private final StockRepository stock;

    public CatalogService(ProductCatalog catalog, StockRepository stock) {
        this.catalog = catalog;
        this.stock = stock;
    }

    /** Productos activos con stock vivo mayor que cero, ordenados por {@code id}. */
    public List<ProductStock> listInStock() {
        return catalog.findAllActive().stream()
                .map(product -> new ProductStock(product, stock.available(product.id())))
                .filter(p -> p.availableStock() > 0)
                .toList();
    }
}
