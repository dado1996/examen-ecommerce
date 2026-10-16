package com.kata.checkout.infrastructure.persistence;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lee {@code products.initial_stock}, que solo siembra el stock en memoria al arrancar: el stock
 * vivo no se persiste. Los productos inactivos no se pueden vender, así que no se siembran.
 */
@Component
public class JdbcInitialStockSource {

    private final JdbcTemplate jdbc;

    public JdbcInitialStockSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, Integer> load() {
        Map<Long, Integer> initialStock = new HashMap<>();
        jdbc.query("SELECT id, initial_stock FROM products WHERE active = 1",
                rs -> {
                    initialStock.put(rs.getLong("id"), rs.getInt("initial_stock"));
                });
        return initialStock;
    }
}
