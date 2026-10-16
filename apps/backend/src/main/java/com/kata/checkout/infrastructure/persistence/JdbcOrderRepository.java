package com.kata.checkout.infrastructure.persistence;

import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.OrderLine;
import com.kata.checkout.domain.port.OrderRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Órdenes sobre SQLite. El dinero viaja como TEXT decimal exacto (ver {@link SqliteMoney}).
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Cabecera, líneas y descuentos en una sola transacción: si algo falla, no queda una orden a medias. */
    @Override
    @Transactional
    public long save(Order order) {
        Long id = jdbc.queryForObject("""
                INSERT INTO checkouts (user_id, coupon_code, subtotal, discount_total, total, max_discount_rate)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id""",
                Long.class,
                order.userId(), order.couponCode(), text(order.subtotal()), text(order.discountTotal()),
                text(order.total()), text(order.maxDiscountRate()));

        jdbc.batchUpdate("""
                INSERT INTO checkout_items (checkout_id, product_id, quantity, unit_price, category, line_subtotal)
                VALUES (?, ?, ?, ?, ?, ?)""",
                order.lines().stream()
                        .map(l -> new Object[] {id, l.productId(), l.quantity(), text(l.unitPrice()), l.category(),
                                text(l.lineSubtotal())})
                        .toList());

        jdbc.batchUpdate("""
                INSERT INTO checkout_discounts (checkout_id, step, rule, rate, reference, base_amount, amount)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                order.discounts().stream()
                        .map(d -> new Object[] {id, d.rule().step(), d.rule().name(), text(d.rate()), d.reference(),
                                text(d.baseAmount()), text(d.amount())})
                        .toList());
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(long id) {
        List<OrderLine> lines = jdbc.query("""
                SELECT product_id, quantity, unit_price, category
                  FROM checkout_items WHERE checkout_id = ? ORDER BY rowid""",
                (rs, n) -> new OrderLine(rs.getLong("product_id"), rs.getInt("quantity"),
                        money(rs, "unit_price"), rs.getString("category")),
                id);
        List<AppliedDiscount> discounts = jdbc.query("""
                SELECT rule, rate, reference, base_amount, amount
                  FROM checkout_discounts WHERE checkout_id = ? ORDER BY step""",
                (rs, n) -> new AppliedDiscount(DiscountRule.valueOf(rs.getString("rule")), money(rs, "rate"),
                        rs.getString("reference"), money(rs, "base_amount"), money(rs, "amount")),
                id);
        return jdbc.query("""
                SELECT user_id, coupon_code, subtotal, discount_total, total, max_discount_rate
                  FROM checkouts WHERE id = ?""",
                (rs, n) -> new Order(nullableLong(rs, "user_id"), rs.getString("coupon_code"),
                        lines, discounts, money(rs, "subtotal"), money(rs, "discount_total"),
                        money(rs, "total"), money(rs, "max_discount_rate")),
                id).stream().findFirst();
    }

    private static String text(BigDecimal amount) {
        return SqliteMoney.toText(amount);
    }

    /** sqlite-jdbc no admite {@code getObject(columna, Long.class)} sobre NULL. */
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        return SqliteMoney.read(rs, column);
    }
}
