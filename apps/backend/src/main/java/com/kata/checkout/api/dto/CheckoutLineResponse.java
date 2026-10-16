package com.kata.checkout.api.dto;

import com.kata.checkout.domain.model.PricedLine;

/** Línea del desglose con el precio del catálogo. */
public record CheckoutLineResponse(long productId, String name, String category, int quantity, String unitPrice,
                                   String lineSubtotal) {

    static CheckoutLineResponse from(PricedLine line) {
        var product = line.product();
        return new CheckoutLineResponse(product.id(), product.name(), product.category(), line.quantity(),
                product.unitPrice().toPlainString(), line.lineSubtotal().toPlainString());
    }
}
