package com.kata.checkout.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Un cuerpo enviado por partes ({@code Transfer-Encoding: chunked}) no declara su tamaño: el filtro
 * corta la lectura al pasar el límite. MockMvc siempre declara el tamaño, así que aquí se oculta.
 */
class BodySizeLimitFilterTest {

    private static final int LIMITE = 64;

    /** Simula una petición por partes: el tamaño del cuerpo no se conoce de antemano. */
    private static final Filter SIN_CONTENT_LENGTH = (request, response, chain) ->
            chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                @Override
                public int getContentLength() {
                    return -1;
                }

                @Override
                public long getContentLengthLong() {
                    return -1;
                }
            }, response);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ControladorQueLeeElCuerpo())
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(SIN_CONTENT_LENGTH, new BodySizeLimitFilter(LIMITE))
            .build();

    @Test
    void unCuerpoSinTamanoDeclaradoQueSuperaElLimiteSeCortaYResponde413() throws Exception {
        enviar("{\"texto\": \"" + "x".repeat(LIMITE) + "\"}")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void unCuerpoSinTamanoDeclaradoDentroDelLimiteSeLeeCompleto() throws Exception {
        enviar("{\"texto\": \"hola\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("hola"));
    }

    @Test
    void elLimiteTambienAplicaAlLeerElCuerpoComoTexto() throws Exception {
        MockHttpServletRequest grande = new MockHttpServletRequest("POST", "/api/v1/checkouts");
        grande.setContent("x".repeat(LIMITE + 1).getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest pequena = new MockHttpServletRequest("POST", "/api/v1/checkouts");
        pequena.setContent("hola".getBytes(StandardCharsets.UTF_8));
        pequena.setCharacterEncoding("UTF-8");

        assertThatThrownBy(() -> leerComoTexto(grande)).isInstanceOf(PayloadTooLargeException.class);
        assertThat(leerComoTexto(pequena)).isEqualTo("hola");
    }

    private static String leerComoTexto(MockHttpServletRequest request) throws Exception {
        StringBuilder texto = new StringBuilder();
        new BodySizeLimitFilter(LIMITE).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try (BufferedReader reader = req.getReader()) {
                reader.lines().forEach(texto::append);
            }
        });
        return texto.toString();
    }

    private ResultActions enviar(String json) throws Exception {
        return mvc.perform(post("/eco").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @RestController
    static class ControladorQueLeeElCuerpo {

        @PostMapping("/eco")
        Map<String, Object> eco(@RequestBody Map<String, Object> cuerpo) {
            return cuerpo;
        }
    }
}
