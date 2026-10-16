package com.kata.checkout.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Límites de tamaño del pedido: como máximo 100 líneas en {@code items} y 16 KB de cuerpo. El
 * límite del cuerpo se aplica antes de deserializar el JSON.
 */
class TamanoDelPedidoApiTest extends ApiIntegrationTest {

    private static final int LIMITE_DEL_CUERPO = 16 * 1024;

    @Test
    void rechazaUnPedidoConMasDe100LineasConValidationError() throws Exception {
        previsualizar(pedidoConLineas(101, 5))
                .andExpect(status().isBadRequest())
                .andExpect(laRespuestaCumpleElContrato())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("items"))
                .andExpect(jsonPath("$.errors[0].message").value("debe tener como máximo 100 productos"));
    }

    @Test
    void elCheckoutTambienRechazaMasDe100Lineas() throws Exception {
        confirmar(pedidoConLineas(101, 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unPedidoDeExactamente100LineasPasaLaValidacionDeTamano() throws Exception {
        // El producto 999 no existe: llegar a PRODUCT_NOT_FOUND prueba que el tamaño fue aceptado
        previsualizar(pedidoConLineas(100, 999))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void rechazaUnCuerpoMayorQue16KbCon413YCodigoEstable() throws Exception {
        previsualizar(cuerpoDe(LIMITE_DEL_CUERPO + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(laRespuestaCumpleElContrato())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void elCheckoutTambienRechazaUnCuerpoDemasiadoGrande() throws Exception {
        confirmar(cuerpoDe(LIMITE_DEL_CUERPO + 1))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(laRespuestaCumpleElContrato())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void unCuerpoDeExactamente16KbSeProcesa() throws Exception {
        previsualizar(cuerpoDe(LIMITE_DEL_CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value("4.50"));
    }

    @Test
    void elLimiteSeMideEnBytesYNoEnCaracteres() throws Exception {
        // "ñ" ocupa 2 bytes en UTF-8: 9000 caracteres son 18000 bytes
        String cupon = "ñ".repeat(9000);
        mvc.perform(post("/api/v1/checkouts/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"items\": [{\"productId\": 5, \"quantity\": 1}], \"couponCode\": \"" + cupon + "\"}")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isPayloadTooLarge());
    }

    private static String pedidoConLineas(int lineas, long productId) {
        String linea = "{\"productId\": " + productId + ", \"quantity\": 1}";
        return "{\"items\": [" + String.join(",", Collections.nCopies(lineas, linea)) + "]}";
    }

    /** Un pedido válido de un cuaderno, rellenado con espacios hasta medir exactamente {@code bytes}. */
    private static String cuerpoDe(int bytes) {
        String pedido = "{\"items\": [{\"productId\": 5, \"quantity\": 1}]}";
        return pedido.substring(0, pedido.length() - 1)
                + " ".repeat(bytes - pedido.length())
                + "}";
    }
}
