package com.kata.checkout.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CheckoutApiTest extends ApiIntegrationTest {

    private static final String DOS_LAPTOPS_Y_LAMPARA_CON_CUPON = """
            {"items": [{"productId": 1, "quantity": 2}, {"productId": 4, "quantity": 1}],
             "couponCode": "WELCOME2026"}""";

    @Test
    void listaLosProductosEnStockConElPrecioComoStringDecimal() throws Exception {
        listarProductos()
                .andExpect(status().isOk())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Laptop 14\""))
                .andExpect(jsonPath("$[0].unitPrice").value("1200.00"))
                .andExpect(jsonPath("$[0].category").value("Tecnología"))
                .andExpect(jsonPath("$[*].unitPrice").value(hasItem("89.90")));
    }

    @Test
    void noListaLosProductosSinStock() throws Exception {
        listarProductos().andExpect(jsonPath("$[*].id").value(not(hasItem(6))));
    }

    @Test
    void laPrevisualizacionDevuelveElDesgloseDelContratoSinDescontarStock() throws Exception {
        int stockAntes = stockDe(3);

        previsualizar("""
                {"items": [{"productId": 3, "quantity": 2}, {"productId": 4, "quantity": 1}],
                 "couponCode": " welcome2026 "}""")
                .andExpect(status().isOk())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.couponCode").value("WELCOME2026"))
                .andExpect(jsonPath("$.lines[0].lineSubtotal").value("179.80"))
                .andExpect(jsonPath("$.discounts[0].rule").value("CATEGORY"))
                .andExpect(jsonPath("$.discounts[0].rate").value("0.10"))
                .andExpect(jsonPath("$.discounts[1].reference").value("100.00"))
                .andExpect(jsonPath("$.subtotal").value("219.80"))
                // 179.80 → 17.98; 201.82 → 10.091; 191.729 → 28.75935; total 56.83035
                .andExpect(jsonPath("$.discountTotal").value("56.83"))
                .andExpect(jsonPath("$.total").value("162.97"))
                .andExpect(jsonPath("$.maxDiscountRate").value("0.35"))
                .andExpect(jsonPath("$.capApplied").value(false));

        assertThat(stockDe(3)).isEqualTo(stockAntes);
    }

    @Test
    void elCheckoutGuardaLaOrdenDescuentaElStockYDevuelveElDesglose() throws Exception {
        int stockAntes = stockDe(1);

        confirmar(DOS_LAPTOPS_Y_LAMPARA_CON_CUPON)
                .andExpect(status().isCreated())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.discounts[2].amount").value(matchesPattern("313\\.50*")))
                .andExpect(jsonPath("$.discountTotal").value("663.50"))
                .andExpect(jsonPath("$.total").value("1776.50"));

        assertThat(stockDe(1)).isEqualTo(stockAntes - 2);
    }

    @Test
    void unCheckoutSinCuponDevuelveElCuponNulo() throws Exception {
        confirmar("""
                {"items": [{"productId": 5, "quantity": 3}]}""")
                .andExpect(status().isCreated())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.couponCode").value(nullValue()))
                .andExpect(jsonPath("$.discounts").isEmpty())
                .andExpect(jsonPath("$.total").value("13.50"));
    }

    @Test
    void rechazaUnPrecioEnviadoPorElCliente() throws Exception {
        previsualizar("""
                {"items": [{"productId": 1, "quantity": 1, "unitPrice": "0.01"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(laRespuestaCumpleElContrato())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].unitPrice"))
                .andExpect(jsonPath("$.errors[0].message").value("propiedad no permitida"));
    }

    @Test
    void rechazaUnaPropiedadDesconocidaEnLaRaiz() throws Exception {
        confirmar("""
                {"items": [{"productId": 1, "quantity": 1}], "total": "1.00"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("total"));
    }

    @Test
    void unCarritoVacioSeRechazaConSuPropioCodigo() throws Exception {
        confirmar("""
                {"items": []}""")
                .andExpect(status().isBadRequest())
                .andExpect(laRespuestaCumpleElContrato())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
    }

    @Test
    void unJsonCorruptoSeRechazaSinFiltrarDetallesInternos() throws Exception {
        previsualizar("{items: [}")
                .andExpect(status().isBadRequest())
                .andExpect(laRespuestaCumpleElContrato())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("body"))
                .andExpect(content().string(not(containsString("jackson"))));
    }

    @Test
    void unaCantidadMenorQueUnoSeRechazaIndicandoElCampo() throws Exception {
        previsualizar("""
                {"items": [{"productId": 1, "quantity": 0}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(laRespuestaCumpleElContrato())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("debe ser mayor o igual que 1"));
    }

    @Test
    void unaCantidadConTipoIncorrectoSeRechaza() throws Exception {
        previsualizar("""
                {"items": [{"productId": 1, "quantity": "2"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"))
                .andExpect(jsonPath("$.errors[0].message").value("tipo de dato inválido"));
        previsualizar("""
                {"items": [{"productId": 1, "quantity": 1.5}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unCarritoSinItemsOConUnItemNuloSeRechaza() throws Exception {
        previsualizar("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        previsualizar("""
                {"items": [null]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0]"));
        previsualizar("""
                {"items": [{"quantity": 1}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].productId"));
    }

    @Test
    void unCuponDeMasDeCincuentaCaracteresSeRechaza() throws Exception {
        previsualizar("""
                {"items": [{"productId": 1, "quantity": 1}], "couponCode": "%s"}""".formatted("X".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("couponCode"));
    }

    @Test
    void unProductoInexistenteSeRechazaConSuIdentificador() throws Exception {
        confirmar("""
                {"items": [{"productId": 999, "quantity": 1}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.productId").value(999));
    }

    @Test
    void unCuponNoRegistradoSeRechazaSinDescontarStock() throws Exception {
        int stockAntes = stockDe(2);

        confirmar("""
                {"items": [{"productId": 2, "quantity": 1}], "couponCode": "BIENVENIDO"}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.code").value("INVALID_COUPON"));

        assertThat(stockDe(2)).isEqualTo(stockAntes);
    }

    @Test
    void elStockInsuficienteSeRechazaEnLaPrevisualizacionYEnElCheckoutSinReservarNada() throws Exception {
        int stockAntes = stockDe(4);
        String pedido = """
                {"items": [{"productId": 4, "quantity": 1}, {"productId": 6, "quantity": 1}]}""";

        previsualizar(pedido)
                .andExpect(status().isConflict())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        confirmar(pedido)
                .andExpect(status().isConflict())
                .andExpect(cumpleElContrato())
                .andExpect(jsonPath("$.productId").value(6))
                .andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.available").value(0));

        assertThat(stockDe(4)).isEqualTo(stockAntes);
    }

    private int stockDe(long productId) throws Exception {
        String json = listarProductos().andReturn().getResponse().getContentAsString();
        List<Integer> stock = JsonPath.read(json, "$[?(@.id == " + productId + ")].availableStock");
        return stock.isEmpty() ? 0 : stock.get(0);
    }
}
