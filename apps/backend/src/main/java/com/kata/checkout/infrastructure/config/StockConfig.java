package com.kata.checkout.infrastructure.config;

import com.kata.checkout.domain.port.StockRepository;
import com.kata.checkout.infrastructure.persistence.JdbcInitialStockSource;
import com.kata.checkout.infrastructure.stock.InMemoryStockRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Siembra el stock en memoria desde el catálogo. Spring Boot ejecuta {@code spring.sql.init}
 * antes de crear los beans que usan {@code JdbcTemplate}, así que las tablas ya existen aquí.
 */
@Configuration
public class StockConfig {

    @Bean
    StockRepository stockRepository(JdbcInitialStockSource initialStock) {
        return new InMemoryStockRepository(initialStock.load());
    }
}
