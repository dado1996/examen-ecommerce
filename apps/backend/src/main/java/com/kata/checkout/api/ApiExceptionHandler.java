package com.kata.checkout.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.kata.checkout.domain.exception.CouponExpiredException;
import com.kata.checkout.domain.exception.EmptyCartException;
import com.kata.checkout.domain.exception.IdempotencyKeyReusedException;
import com.kata.checkout.domain.exception.InsufficientStockException;
import com.kata.checkout.domain.exception.InvalidCouponException;
import com.kata.checkout.domain.exception.ProductNotFoundException;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce errores a Problem Details (RFC 9457) con un {@code code} de negocio estable.
 * Los errores de persistencia se registran completos en el log, pero la respuesta nunca
 * incluye el mensaje de SQLite, el SQL ni la traza.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para que los errores de lectura y validación
 * del cuerpo también salgan como {@code VALIDATION_ERROR}, y reemplaza al manejador que Spring Boot
 * registra con {@code spring.mvc.problemdetails.enabled}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Un error de validación del cuerpo: {@code field} usa la notación {@code items[0].quantity}. */
    public record FieldError(String field, String message) {
    }

    /** Campos que no cumplen el contrato: tipos, nulos, cantidades menores que 1, cupón demasiado largo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = Stream.concat(
                        e.getBindingResult().getFieldErrors().stream()
                                .map(f -> new FieldError(f.getField(), f.getDefaultMessage())),
                        e.getBindingResult().getGlobalErrors().stream()
                                .map(g -> new FieldError(g.getObjectName(), g.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /** JSON corrupto, tipo incorrecto o una propiedad no permitida (p. ej. un precio). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(validationProblem(List.of(unreadableField(e))));
    }

    /** Un header o parámetro con un tipo inválido, p. ej. una {@code Idempotency-Key} que no es UUID. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String field = e instanceof MethodArgumentTypeMismatchException argument ? argument.getName() : e.getPropertyName();
        return ResponseEntity.badRequest().body(validationProblem(List.of(new FieldError(field, "formato inválido"))));
    }

    @ExceptionHandler(EmptyCartException.class)
    ProblemDetail emptyCart(EmptyCartException e) {
        return problem(HttpStatus.BAD_REQUEST, "EMPTY_CART", "Carrito vacío", "El carrito no tiene productos.");
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail productNotFound(ProductNotFoundException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_FOUND",
                "Producto no encontrado", "El producto " + e.productId() + " no existe.");
        problem.setProperty("productId", e.productId());
        return problem;
    }

    @ExceptionHandler(InvalidCouponException.class)
    ProblemDetail invalidCoupon(InvalidCouponException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COUPON", "Cupón inválido",
                "El cupón no está registrado.");
    }

    @ExceptionHandler(CouponExpiredException.class)
    ProblemDetail couponExpired(CouponExpiredException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "COUPON_EXPIRED", "Cupón vencido", "El cupón está vencido.");
    }

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Stock insuficiente",
                "No hay unidades suficientes del producto " + e.productId() + ".");
        problem.setProperty("productId", e.productId());
        problem.setProperty("requested", e.requested());
        problem.setProperty("available", e.available());
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Clave de idempotencia reutilizada",
                "La clave de idempotencia ya se usó con otra solicitud.");
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

    private static ProblemDetail validationProblem(List<FieldError> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Solicitud inválida",
                "El cuerpo de la solicitud no es válido.");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Ubica el campo a partir de la ruta de Jackson, sin exponer su mensaje (nombra clases internas). */
    private static FieldError unreadableField(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String message = mapping instanceof UnrecognizedPropertyException
                    ? "propiedad no permitida"
                    : "tipo de dato inválido";
            return new FieldError(path(mapping.getPath()), message);
        }
        return new FieldError("body", "el cuerpo no es un JSON válido");
    }

    private static String path(List<JsonMappingException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : references) {
            if (reference.getFieldName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getFieldName());
            } else {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
