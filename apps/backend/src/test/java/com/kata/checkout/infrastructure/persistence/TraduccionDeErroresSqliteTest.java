package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLErrorCodes;
import org.springframework.jdbc.support.SQLErrorCodesFactory;

/** Valida sql-error-codes.xml: los códigos de SQLite se traducen a excepciones de Spring con sentido. */
class TraduccionDeErroresSqliteTest {

    private final SQLErrorCodes codigos = SQLErrorCodesFactory.getInstance().getErrorCodes("SQLite");
    private final SQLErrorCodeSQLExceptionTranslator traductor = new SQLErrorCodeSQLExceptionTranslator(codigos);

    @Test
    void registraLosCodigosDeSqliteParaElNombreQueReportaElDriver() {
        assertThat(codigos.getDatabaseProductNames()).containsExactly("SQLite");
        assertThat(codigos.getDataIntegrityViolationCodes()).containsExactly("19");
    }

    @Test
    void unaViolacionDeRestriccionSeTraduceAViolacionDeIntegridad() {
        // sqlite-jdbc informa SQLITE_CONSTRAINT (19) sin SQLState
        DataAccessException traducida = traducir(new SQLException(
                "[SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint failed", null, 19));

        assertThat(traducida).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaBaseOcupadaOBloqueadaSeTraduceAFalloDeBloqueo() {
        assertThat(traducir(new SQLException("[SQLITE_BUSY]", null, 5))).isInstanceOf(CannotAcquireLockException.class);
        assertThat(traducir(new SQLException("[SQLITE_LOCKED]", null, 6))).isInstanceOf(CannotAcquireLockException.class);
    }

    private DataAccessException traducir(SQLException e) {
        return traductor.translate("prueba", "SELECT 1", e);
    }
}
