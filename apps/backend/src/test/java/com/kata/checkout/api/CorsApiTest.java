package com.kata.checkout.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Preflight CORS que hace el navegador antes de una petición con headers propios. MockMvc no lo
 * hace por su cuenta, así que sin estas pruebas un header nuevo puede pasar todas las pruebas de
 * la API y fallar en el navegador.
 */
class CorsApiTest extends ApiIntegrationTest {

    private static final String FRONTEND = "http://localhost:4200";

    @Test
    void elPreflightDelCheckoutPermiteElHeaderIdempotencyKeyDesdeElFrontend() throws Exception {
        mvc.perform(options("/api/v1/checkouts")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        containsStringIgnoringCase("idempotency-key")));
    }

    @Test
    void elPreflightDelPreviewPermiteContentTypeDesdeElFrontend() throws Exception {
        mvc.perform(options("/api/v1/checkouts/preview")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    void rechazaElPreflightDesdeUnOrigenNoConfigurado() throws Exception {
        mvc.perform(options("/api/v1/checkouts")
                        .header("Origin", "https://otro-sitio.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,idempotency-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rechazaElPreflightConUnHeaderNoPermitido() throws Exception {
        mvc.perform(options("/api/v1/checkouts")
                        .header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isForbidden());
    }
}
