package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataRetrievalFailureException;

class SqliteMoneyTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "1776.50", "313.500000", "0.0000000001", "12345678901234567890.99"})
    void escribeYLeeElDecimalExactoConSuEscala(String valor) throws SQLException {
        BigDecimal original = new BigDecimal(valor);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("monto")).thenReturn(SqliteMoney.toText(original));

        assertThat(SqliteMoney.read(rs, "monto")).isEqualTo(original);
    }

    @Test
    void escribeSinNotacionCientifica() {
        assertThat(SqliteMoney.toText(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(SqliteMoney.toText(new BigDecimal("1E-7"))).isEqualTo("0.0000001");
    }

    @Test
    void conservaUnValorQueUnDoublePerderia() throws SQLException {
        String valor = "9007199254740993.01"; // 2^53 + 1: no cabe exacto en un double
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("monto")).thenReturn(valor);

        assertThat(SqliteMoney.read(rs, "monto").toPlainString()).isEqualTo(valor);
        assertThat(new BigDecimal(Double.parseDouble(valor)).toPlainString()).isNotEqualTo(valor);
    }

    @Test
    void nuncaLeeElDineroComoPuntoFlotante() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("monto")).thenReturn("10.00");

        SqliteMoney.read(rs, "monto");

        verify(rs, never()).getDouble(anyString());
        verify(rs, never()).getFloat(anyString());
        verify(rs, never()).getBigDecimal(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "10,50", "1.2.3"})
    void rechazaUnMontoCorruptoConUnErrorDeAccesoADatos(String corrupto) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("total")).thenReturn(corrupto);

        assertThatThrownBy(() -> SqliteMoney.read(rs, "total"))
                .isInstanceOf(DataRetrievalFailureException.class)
                .hasMessageContaining("total");
    }

    @Test
    void rechazaUnMontoNuloComoDatoCorrupto() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("subtotal")).thenReturn(null);

        assertThatThrownBy(() -> SqliteMoney.read(rs, "subtotal"))
                .isInstanceOf(DataRetrievalFailureException.class)
                .hasMessageContaining("subtotal");
    }
}
