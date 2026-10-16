package com.kata.checkout.api;

import com.kata.checkout.domain.exception.InsufficientStockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce errores a Problem Details (RFC 9457) con un {@code code} de negocio estable.
 * Los errores de persistencia se registran completos en el log, pero la respuesta nunca
 * incluye el mensaje de SQLite, el SQL ni la traza.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Stock insuficiente",
                "No hay unidades suficientes del producto " + e.productId() + ".");
        problem.setProperty("productId", e.productId());
        problem.setProperty("requested", e.requested());
        problem.setProperty("available", e.available());
        return problem;
    }

    /** FK o CHECK rechazados por la base: la orden tenía datos inconsistentes y no se guardó. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrityViolation(DataIntegrityViolationException e) {
        LOG.warn("Orden rechazada por una restricción de la base", e);
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "DATA_INTEGRITY_VIOLATION", "Datos inconsistentes",
                "La orden contiene datos que no cumplen las reglas del catálogo y no se guardó.");
    }

    /** Base inaccesible, ocupada o bloqueada: es transitorio, el cliente puede reintentar. */
    @ExceptionHandler({DataAccessResourceFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail databaseUnavailable(DataAccessException e) {
        LOG.error("Base de datos no disponible", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Servicio no disponible",
                "No fue posible acceder a los datos. Intenta de nuevo en unos segundos.");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail persistenceError(DataAccessException e) {
        LOG.error("Error de persistencia", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "PERSISTENCE_ERROR", "Error interno",
                "Ocurrió un error al procesar los datos.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
