package com.kata.checkout.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class HealthApiTest extends ApiIntegrationTest {

    @Test
    void elEstadoDelServicioEstaDisponibleEnHealthYReportaUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void elEstadoDelServicioNoExponeDetallesInternos() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }
}
