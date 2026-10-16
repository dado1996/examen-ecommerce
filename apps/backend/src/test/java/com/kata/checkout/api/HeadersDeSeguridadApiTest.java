package com.kata.checkout.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Headers de seguridad en todas las respuestas, incluidos los errores y {@code /health}. */
class HeadersDeSeguridadApiTest extends ApiIntegrationTest {

    private static final String POLITICA_DE_LA_API =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Test
    void lasRespuestasDeLaApiIncluyenLosHeadersDeSeguridad() throws Exception {
        conHeadersDeSeguridad(listarProductos().andExpect(status().isOk()));
    }

    @Test
    void losErroresTambienIncluyenLosHeadersDeSeguridad() throws Exception {
        conHeadersDeSeguridad(previsualizar("{ no es json").andExpect(status().isBadRequest()));
    }

    @Test
    void elEstadoDelServicioTambienIncluyeLosHeadersDeSeguridad() throws Exception {
        conHeadersDeSeguridad(mvc.perform(get("/health")).andExpect(status().isOk()));
    }

    @Test
    void swaggerUiRecibeUnaPoliticaQueLePermiteCargarSusPropiosRecursos() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("default-src 'none'"))));
    }

    private static void conHeadersDeSeguridad(ResultActions respuesta) throws Exception {
        respuesta
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", POLITICA_DE_LA_API));
    }
}
