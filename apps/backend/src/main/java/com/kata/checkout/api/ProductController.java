package com.kata.checkout.api;

import com.kata.checkout.api.dto.ProductResponse;
import com.kata.checkout.application.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/products} (HU1). */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return catalog.listInStock().stream().map(ProductResponse::from).toList();
    }
}
