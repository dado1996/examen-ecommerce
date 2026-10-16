package com.kata.checkout.infrastructure.persistence;

import com.kata.checkout.domain.model.Product;
import com.kata.checkout.domain.port.ProductCatalog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Catálogo sobre la tabla {@code products}. Solo expone los productos activos. */
@Repository
public class JdbcProductCatalog implements ProductCatalog {

    private static final String SELECT_ACTIVE =
            "SELECT id, name, category, unit_price FROM products WHERE active = 1";

    private final JdbcTemplate jdbc;

    public JdbcProductCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Product> findAllActive() {
        return jdbc.query(SELECT_ACTIVE + " ORDER BY id", JdbcProductCatalog::product);
    }

    @Override
    public Optional<Product> findActiveById(long id) {
        return jdbc.query(SELECT_ACTIVE + " AND id = ?", JdbcProductCatalog::product, id).stream().findFirst();
    }

    private static Product product(ResultSet rs, int rowNum) throws SQLException {
        return new Product(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                SqliteMoney.read(rs, "unit_price"));
    }
}
