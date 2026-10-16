package com.kata.checkout.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de las pruebas de la API sobre la aplicación completa y un SQLite temporal propio. Cada
 * respuesta se valida contra {@code packages/contracts/openapi.yaml}.
 *
 * <p>Todas las peticiones de MockMvc salen de 127.0.0.1: el rate limiting se sube aquí para que las
 * pruebas no se bloqueen entre sí. {@code RateLimitingApiTest} lo prueba con límites bajos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.limits.checkout-rate.requests=100000",
        "app.limits.coupon-failures.requests=100000"})
abstract class ApiIntegrationTest {

    private static final Path DB_FILE = crearArchivoTemporal();
    private static final String CONTRATO = leerContrato();

    /** Valida la petición y la respuesta: para los casos que cumplen el contrato. */
    static final OpenApiInteractionValidator CONTRATO_COMPLETO =
            OpenApiInteractionValidator.createForInlineApiSpecification(CONTRATO).withResolveCombinators(true).build();

    /** Valida solo la respuesta: para las peticiones inválidas a propósito. */
    private static final OpenApiInteractionValidator SOLO_RESPUESTA =
            OpenApiInteractionValidator.createForInlineApiSpecification(CONTRATO).withResolveCombinators(true)
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request", ValidationReport.Level.IGNORE)
                            .build())
                    .build();

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void baseDeDatosTemporal(DynamicPropertyRegistry registry) {
        registry.add("APP_DB_PATH", DB_FILE::toString);
    }

    protected static ResultMatcher cumpleElContrato() {
        return openApi().isValid(CONTRATO_COMPLETO);
    }

    protected static ResultMatcher laRespuestaCumpleElContrato() {
        return openApi().isValid(SOLO_RESPUESTA);
    }

    protected ResultActions listarProductos() throws Exception {
        return mvc.perform(get("/api/v1/products").accept(MediaType.APPLICATION_JSON));
    }

    protected ResultActions previsualizar(String json) throws Exception {
        return mvc.perform(post("/api/v1/checkouts/preview").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions confirmar(String json) throws Exception {
        return mvc.perform(post("/api/v1/checkouts").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions confirmar(String json, String idempotencyKey) throws Exception {
        return mvc.perform(post("/api/v1/checkouts").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey).content(json));
    }

    private static String leerContrato() {
        try {
            return Files.readString(Path.of("../../packages/contracts/openapi.yaml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path crearArchivoTemporal() {
        try {
            Path file = Files.createTempFile("checkout-api-", ".db");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
