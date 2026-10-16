package com.kata.checkout.api.dto;

import com.kata.checkout.application.ProductStock;

/** Producto en stock. {@code unitPrice} viaja como string decimal; {@code availableStock} es el stock vivo. */
public record ProductResponse(long id, String name, String unitPrice, String category, int availableStock) {

    public static ProductResponse from(ProductStock productStock) {
        var product = productStock.product();
        return new ProductResponse(product.id(), product.name(), product.unitPrice().toPlainString(),
                product.category(), productStock.availableStock());
    }
}
