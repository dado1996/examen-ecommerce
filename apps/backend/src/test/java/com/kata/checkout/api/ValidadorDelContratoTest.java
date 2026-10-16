package com.kata.checkout.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import org.junit.jupiter.api.Test;

/** Verifica que la validación del contrato que usan las pruebas de la API sí detecta desviaciones. */
class ValidadorDelContratoTest {

    @Test
    void aceptaUnProductoQueCumpleElContrato() {
        assertThat(validarProductos("""
                [{"id": 1, "name": "Laptop", "unitPrice": "1200.00", "category": "Tecnología", "availableStock": 3}]""")
                .hasErrors()).isFalse();
    }

    @Test
    void detectaUnPrecioEnviadoComoNumeroJson() {
        assertThat(validarProductos("""
                [{"id": 1, "name": "Laptop", "unitPrice": 1200.00, "category": "Tecnología", "availableStock": 3}]""")
                .hasErrors()).isTrue();
    }

    @Test
    void detectaUnCampoObligatorioAusente() {
        assertThat(validarProductos("""
                [{"id": 1, "name": "Laptop", "unitPrice": "1200.00", "category": "Tecnología"}]""")
                .hasErrors()).isTrue();
    }

    @Test
    void detectaUnProductoSinStockEnElListado() {
        assertThat(validarProductos("""
                [{"id": 1, "name": "Laptop", "unitPrice": "1200.00", "category": "Tecnología", "availableStock": 0}]""")
                .hasErrors()).isTrue();
    }

    private static ValidationReport validarProductos(String body) {
        return ApiIntegrationTest.CONTRATO_COMPLETO.validateResponse("/api/v1/products", Request.Method.GET,
                SimpleResponse.Builder.ok().withContentType("application/json").withBody(body).build());
    }
}
