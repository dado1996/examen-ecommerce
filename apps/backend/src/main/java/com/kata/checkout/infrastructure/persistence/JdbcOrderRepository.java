package com.kata.checkout.infrastructure.persistence;

import com.kata.checkout.domain.discount.DiscountResult;
import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.CheckoutBreakdown;
import com.kata.checkout.domain.model.DiscountRule;
import com.kata.checkout.domain.model.Idempotency;
import com.kata.checkout.domain.model.Order;
import com.kata.checkout.domain.model.OrderLine;
import com.kata.checkout.domain.model.PricedLine;
import com.kata.checkout.domain.model.Product;
import com.kata.checkout.domain.model.SavedOrder;
import com.kata.checkout.domain.model.StoredCheckout;
import com.kata.checkout.domain.port.OrderRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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

    /** Se redefine para que el proxy la abra en una transacción: la del puerto llamaría al otro save sin ella. */
    @Override
    @Transactional
    public SavedOrder save(Order order) {
        return save(order, null);
    }

    /** Cabecera, líneas y descuentos en una sola transacción: si algo falla, no queda una orden a medias. */
    @Override
    @Transactional
    public SavedOrder save(Order order, Idempotency idempotency) {
        SavedOrder saved = jdbc.queryForObject("""
                INSERT INTO checkouts (user_id, coupon_code, subtotal, discount_total, total, max_discount_rate,
                                       idempotency_key, request_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, created_at""",
                (rs, n) -> new SavedOrder(rs.getLong("id"), Instant.parse(rs.getString("created_at"))),
                order.userId(), order.couponCode(), text(order.subtotal()), text(order.discountTotal()),
                text(order.total()), text(order.maxDiscountRate()),
                idempotency == null ? null : idempotency.key(),
                idempotency == null ? null : idempotency.requestHash());
        long id = saved.id();

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
        return saved;
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
        List<AppliedDiscount> discounts = discounts(id);
        return jdbc.query("""
                SELECT user_id, coupon_code, subtotal, discount_total, total, max_discount_rate
                  FROM checkouts WHERE id = ?""",
                (rs, n) -> new Order(nullableLong(rs, "user_id"), rs.getString("coupon_code"),
                        lines, discounts, money(rs, "subtotal"), money(rs, "discount_total"),
                        money(rs, "total"), money(rs, "max_discount_rate")),
                id).stream().findFirst();
    }

    /**
     * Rearma el desglose con las copias de precio y categoría que guardó la orden; el nombre sale
     * del catálogo, que nunca borra un producto vendido (aunque esté inactivo).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<StoredCheckout> findByIdempotencyKey(String key) {
        // Primero la cabecera y después las líneas: no se anida una consulta mientras otra está abierta
        record Header(SavedOrder saved, String requestHash, BigDecimal subtotal, BigDecimal discountTotal,
                      BigDecimal total, BigDecimal maxDiscountRate) {
        }
        return jdbc.query("""
                SELECT id, created_at, request_hash, subtotal, discount_total, total, max_discount_rate
                  FROM checkouts WHERE idempotency_key = ?""",
                (rs, n) -> new Header(new SavedOrder(rs.getLong("id"), Instant.parse(rs.getString("created_at"))),
                        rs.getString("request_hash"), money(rs, "subtotal"), money(rs, "discount_total"),
                        money(rs, "total"), money(rs, "max_discount_rate")),
                key).stream().findFirst()
                .map(h -> new StoredCheckout(h.saved().id(), h.saved().createdAt(), h.requestHash(),
                        new CheckoutBreakdown(pricedLines(h.saved().id()),
                                new DiscountResult(discounts(h.saved().id()), h.subtotal(), h.discountTotal(),
                                        h.total(), h.maxDiscountRate()))));
    }

    private List<PricedLine> pricedLines(long checkoutId) {
        return jdbc.query("""
                SELECT i.product_id, p.name, i.category, i.unit_price, i.quantity
                  FROM checkout_items i JOIN products p ON p.id = i.product_id
                 WHERE i.checkout_id = ? ORDER BY i.rowid""",
                (rs, n) -> new PricedLine(new Product(rs.getLong("product_id"), rs.getString("name"),
                        rs.getString("category"), money(rs, "unit_price")), rs.getInt("quantity")),
                checkoutId);
    }

    private List<AppliedDiscount> discounts(long checkoutId) {
        return jdbc.query("""
                SELECT rule, rate, reference, base_amount, amount
                  FROM checkout_discounts WHERE checkout_id = ? ORDER BY step""",
                (rs, n) -> new AppliedDiscount(DiscountRule.valueOf(rs.getString("rule")), money(rs, "rate"),
                        rs.getString("reference"), money(rs, "base_amount"), money(rs, "amount")),
                checkoutId);
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
