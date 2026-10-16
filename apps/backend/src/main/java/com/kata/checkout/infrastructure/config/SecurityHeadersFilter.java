package com.kata.checkout.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Headers de seguridad en todas las respuestas, también en los errores y en {@code /health}.
 *
 * <p>La API solo devuelve JSON, así que su Content-Security-Policy no permite cargar nada
 * ({@code default-src 'none'}) ni incrustarla en otro sitio ({@code frame-ancestors 'none'}): si una
 * respuesta se abriera como página, el navegador no ejecutaría nada. Swagger UI sí es una página y
 * necesita sus propios scripts y estilos, así que recibe una política limitada a su origen.
 * {@code Strict-Transport-Security} queda fuera: solo tiene efecto sobre HTTPS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String API_POLICY = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    static final String SWAGGER_UI_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        boolean swaggerUi = request.getRequestURI().startsWith(request.getContextPath() + "/swagger-ui");
        response.setHeader("Content-Security-Policy", swaggerUi ? SWAGGER_UI_POLICY : API_POLICY);
        chain.doFilter(request, response);
    }
}
