package com.kata.checkout.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class ConexionYEsquemaTest extends SqliteIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laUrlDeConexionActivaLasLlavesForaneas() {
        assertThat(((HikariDataSource) dataSource).getJdbcUrl())
                .startsWith("jdbc:sqlite:")
                .endsWith("?foreign_keys=true");
    }

    @Test
    void elPoolUsaUnaSolaConexionParaEvitarEscritoresConcurrentes() {
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);
    }

    @Test
    void cadaConexionNuevaDelDriverTieneLasLlavesForaneasActivas() throws SQLException {
        // Se evita el pool: una conexión recién abierta con la misma URL ya trae el PRAGMA
        String url = ((HikariDataSource) dataSource).getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void laVersionDeSqliteAdmiteTablasStrict() {
        String version = jdbc.queryForObject("SELECT sqlite_version()", String.class);
        String[] partes = version.split("\\.");

        assertThat(Integer.parseInt(partes[0]) * 1000 + Integer.parseInt(partes[1])).isGreaterThanOrEqualTo(3037);
    }

    @Test
    void creaTodasLasTablasComoStrictYLaVistaDeResumen() {
        assertThat(jdbc.queryForList(
                "SELECT name FROM pragma_table_list WHERE schema = 'main' AND type = 'table' AND strict = 1"
                        + " AND name NOT LIKE 'sqlite_%' ORDER BY name", String.class))
                .containsExactly("checkout_discounts", "checkout_items", "checkouts", "products", "users");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sqlite_master WHERE type = 'view' AND name = 'checkout_summary'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void cargaLosDatosDeDemostracionConPreciosComoTextoExacto() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT typeof(unit_price) FROM products", String.class))
                .hasSize(6)
                .containsOnly("text");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM products WHERE id = 3", String.class))
                .isEqualTo("89.90");
    }

    @Test
    void volverAEjecutarElEsquemaYElSeedNoDuplicaNiFalla() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/schema.sql"), new ClassPathResource("db/seed.sql"));
        populator.setSqlScriptEncoding("UTF-8");

        populator.execute(dataSource);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(2);
    }

    @Test
    void losScriptsEmpaquetadosSonIdenticosALosDePackagesSchema() throws IOException {
        // Surefire ejecuta desde apps/backend; packages/schema es la única fuente de verdad
        Path fuente = Path.of("../../packages/schema");
        for (String script : new String[] {"schema.sql", "seed.sql"}) {
            assertThat(new ClassPathResource("db/" + script).getContentAsByteArray())
                    .as(script)
                    .isEqualTo(Files.readAllBytes(fuente.resolve(script)));
        }
    }

    @Test
    void unErrorDeSqlQueNoEsDeIntegridadLlegaComoErrorDeAccesoADatosDeSpring() {
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT * FROM tabla_inexistente", Integer.class))
                .isInstanceOf(DataAccessException.class)
                .isNotInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(SQLException.class);
    }
}
