package com.kata.checkout.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.dao.DataRetrievalFailureException;

/**
 * Conversión del dinero entre {@link BigDecimal} y el TEXT decimal exacto de SQLite.
 * Nunca pasa por {@code double}: se lee con {@code new BigDecimal(rs.getString(...))}.
 */
final class SqliteMoney {

    private SqliteMoney() {
    }

    static String toText(BigDecimal amount) {
        return amount.toPlainString();
    }

    /**
     * @throws DataRetrievalFailureException si la columna es NULL o no es un decimal válido,
     *                                       para que un dato corrupto no escape como
     *                                       {@code NumberFormatException}
     */
    static BigDecimal read(ResultSet rs, String column) throws SQLException {
        String text = rs.getString(column);
        if (text == null) {
            throw new DataRetrievalFailureException("Monto nulo en la columna " + column);
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new DataRetrievalFailureException("Monto corrupto en la columna " + column, e);
        }
    }
}
