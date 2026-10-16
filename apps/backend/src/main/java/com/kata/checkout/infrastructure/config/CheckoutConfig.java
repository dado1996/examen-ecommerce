package com.kata.checkout.infrastructure.config;

import com.kata.checkout.application.CatalogService;
import com.kata.checkout.application.CheckoutService;
import com.kata.checkout.application.UnitOfWork;
import com.kata.checkout.domain.discount.DiscountEngine;
import com.kata.checkout.domain.port.OrderRepository;
import com.kata.checkout.domain.port.ProductCatalog;
import com.kata.checkout.domain.port.StockRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Conecta los casos de uso y el motor de descuentos, que no dependen de Spring, con sus adaptadores. */
@Configuration
public class CheckoutConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DiscountEngine discountEngine(DiscountProperties properties, Clock clock) {
        return DiscountEngineFactory.create(properties, clock);
    }

    @Bean
    CatalogService catalogService(ProductCatalog catalog, StockRepository stock) {
        return new CatalogService(catalog, stock);
    }

    @Bean
    CheckoutService checkoutService(ProductCatalog catalog, StockRepository stock, OrderRepository orders,
                                    DiscountEngine discountEngine, UnitOfWork transactions) {
        return new CheckoutService(catalog, stock, orders, discountEngine, transactions);
    }
}
