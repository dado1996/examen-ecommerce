package com.kata.checkout.infrastructure.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base de las pruebas de integración con SQLite. Usa un archivo temporal y solo
 * sobrescribe la ruta: la URL (con {@code ?foreign_keys=true}) y {@code spring.sql.init}
 * son las mismas de {@code application.yml}, así que se prueba la configuración real.
 */
@SpringBootTest
abstract class SqliteIntegrationTest {

    private static final Path DB_FILE = crearArchivoTemporal();

    @DynamicPropertySource
    static void baseDeDatosTemporal(DynamicPropertyRegistry registry) {
        registry.add("APP_DB_PATH", DB_FILE::toString);
    }

    private static Path crearArchivoTemporal() {
        try {
            Path file = Files.createTempFile("checkout-it-", ".db");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
