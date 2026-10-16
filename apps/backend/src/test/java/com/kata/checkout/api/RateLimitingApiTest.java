package com.kata.checkout.api;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Rate limiting por IP en {@code POST /checkouts} y {@code /checkouts/preview}, con límites bajos
 * para la prueba: 5 peticiones por minuto (una cada 12 s) y 2 cupones rechazados cada 15 minutos.
 * El contexto se comparte entre las pruebas, así que cada una usa su propia IP.
 */
@TestPropertySource(properties = {
        "app.limits.checkout-rate.requests=5",
        "app.limits.checkout-rate.window=1m",
        "app.limits.coupon-failures.requests=2",
        "app.limits.coupon-failures.window=15m"})
class RateLimitingApiTest extends ApiIntegrationTest {

    private static final String FRONTEND = "http://localhost:4200";
    private static final String UN_CUADERNO = """
            {"items": [{"productId": 5, "quantity": 1}]}""";
    private static final String CUPON_INEXISTENTE = """
            {"items": [{"productId": 5, "quantity": 1}], "couponCode": "ADIVINANZA"}""";
    private static final String CUPON_VALIDO = """
            {"items": [{"productId": 5, "quantity": 1}], "couponCode": "WELCOME2026"}""";

    @Test
    void permiteHastaElLimiteYLuegoResponde429ConRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            previsualizarDesde("10.1.0.1", UN_CUADERNO).andExpect(status().isOk());
        }

        previsualizarDesde("10.1.0.1", UN_CUADERNO)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(cumpleElContrato())
                // Una petición cada 12 s; el margen cubre lo que tardan las cinco anteriores
                .andExpect(header().string("Retry-After", matchesPattern("1[0-2]")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void elPreviewYElCheckoutCompartenElLimite() throws Exception {
        for (int i = 0; i < 5; i++) {
            previsualizarDesde("10.1.0.2", UN_CUADERNO).andExpect(status().isOk());
        }

        mvc.perform(desde("10.1.0.2", post("/api/v1/checkouts").contentType(MediaType.APPLICATION_JSON)
                        .content(UN_CUADERNO)))
                .andExpect(status().isTooManyRequests())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void elLimiteEsPorIpYNoAfectaAOtrosClientes() throws Exception {
        for (int i = 0; i < 6; i++) {
            previsualizarDesde("10.1.0.3", UN_CUADERNO);
        }

        previsualizarDesde("10.1.0.4", UN_CUADERNO).andExpect(status().isOk());
    }

    @Test
    void elListadoDeProductosYElPreflightNoConsumenElLimite() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(desde("10.1.0.5", get("/api/v1/products"))).andExpect(status().isOk());
            mvc.perform(desde("10.1.0.5", options("/api/v1/checkouts/preview")
                            .header("Origin", FRONTEND)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type")))
                    .andExpect(status().isOk());
        }

        previsualizarDesde("10.1.0.5", UN_CUADERNO).andExpect(status().isOk());
    }

    @Test
    void variosCuponesRechazadosBloqueanLaIpAunqueLuegoUseUnCuponValido() throws Exception {
        previsualizarDesde("10.1.0.6", CUPON_INEXISTENTE).andExpect(jsonPath("$.code").value("INVALID_COUPON"));
        previsualizarDesde("10.1.0.6", CUPON_INEXISTENTE).andExpect(jsonPath("$.code").value("INVALID_COUPON"));

        // Bloqueado aunque el cupón exista: la respuesta no confirma si un código es válido
        previsualizarDesde("10.1.0.6", CUPON_VALIDO)
                .andExpect(status().isTooManyRequests())
                .andExpect(cumpleElContrato())
                .andExpect(header().string("Retry-After", matchesPattern("44[0-9]|450")))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void unCuponValidoNoCuentaComoIntentoFallido() throws Exception {
        for (int i = 0; i < 3; i++) {
            previsualizarDesde("10.1.0.7", CUPON_VALIDO).andExpect(status().isOk());
        }
    }

    @Test
    void losCuponesRechazadosDeUnaIpNoBloqueanAOtra() throws Exception {
        previsualizarDesde("10.1.0.8", CUPON_INEXISTENTE);
        previsualizarDesde("10.1.0.8", CUPON_INEXISTENTE);

        previsualizarDesde("10.1.0.9", CUPON_VALIDO).andExpect(status().isOk());
    }

    @Test
    void la429LlevaLosHeadersCorsParaQueElFrontendLeaElCodigoYRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            previsualizarDesde("10.1.0.10", UN_CUADERNO);
        }

        mvc.perform(desde("10.1.0.10", post("/api/v1/checkouts/preview").contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", FRONTEND)
                        .content(UN_CUADERNO)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().string("Access-Control-Expose-Headers", containsStringIgnoringCase("Retry-After")));
    }

    private ResultActions previsualizarDesde(String ip, String json) throws Exception {
        return mvc.perform(desde(ip, post("/api/v1/checkouts/preview").contentType(MediaType.APPLICATION_JSON)
                .content(json)));
    }

    private static MockHttpServletRequestBuilder desde(String ip, MockHttpServletRequestBuilder request) {
        RequestPostProcessor conIp = r -> {
            r.setRemoteAddr(ip);
            return r;
        };
        return request.with(conIp);
    }
}
