package com.kata.checkout.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kata.checkout.domain.exception.InsufficientStockException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private static final String MENSAJE_INTERNO =
            "[SQLITE_CONSTRAINT_FOREIGNKEY] FOREIGN KEY constraint failed en INSERT INTO checkout_items";

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ControladorQueFalla())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void unaViolacionDeFkOCheckSeEntregaComoErrorDeNegocioConCodigoEstable() throws Exception {
        pedir("integridad")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void laRespuestaDeIntegridadNoFiltraElMensajeDeSqliteNiLaTraza() throws Exception {
        pedir("integridad")
                .andExpect(content().string(not(containsString("SQLITE"))))
                .andExpect(content().string(not(containsString("INSERT"))))
                .andExpect(content().string(not(containsString("checkout_items"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void unaBaseNoDisponibleSeEntregaComoServicioNoDisponible() throws Exception {
        pedir("sin-conexion")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("/ruta/secreta"))));
    }

    @Test
    void unaBaseOcupadaSeEntregaComoServicioNoDisponible() throws Exception {
        pedir("bloqueada")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
    }

    @Test
    void cualquierOtroErrorDeSqlSeEntregaComoErrorInternoSinDetalles() throws Exception {
        pedir("dato-corrupto")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PERSISTENCE_ERROR"))
                .andExpect(content().string(not(containsString("subtotal"))));
    }

    @Test
    void elStockInsuficienteSeEntregaComoConflictoConElProducto() throws Exception {
        pedir("sin-stock")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.productId").value(6))
                .andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.available").value(0));
    }

    private ResultActions pedir(String caso) throws Exception {
        return mvc.perform(get("/falla/" + caso).accept(MediaType.APPLICATION_JSON));
    }

    @RestController
    static class ControladorQueFalla {

        @GetMapping("/falla/{caso}")
        String fallar(@PathVariable String caso) {
            throw switch (caso) {
                case "integridad" -> new DataIntegrityViolationException(MENSAJE_INTERNO);
                case "sin-conexion" -> new CannotGetJdbcConnectionException(
                        "No se pudo abrir /ruta/secreta/checkout.db", new SQLException("path"));
                case "bloqueada" -> new CannotAcquireLockException("[SQLITE_BUSY] database is locked");
                case "dato-corrupto" -> new DataRetrievalFailureException("Monto corrupto en la columna subtotal");
                case "sin-stock" -> new InsufficientStockException(6, 1, 0);
                default -> new IllegalStateException(caso);
            };
        }
    }
}
