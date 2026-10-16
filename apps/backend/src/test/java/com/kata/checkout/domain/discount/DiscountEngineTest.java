package com.kata.checkout.domain.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kata.checkout.domain.exception.CouponExpiredException;
import com.kata.checkout.domain.exception.InvalidCouponException;
import com.kata.checkout.domain.model.AppliedDiscount;
import com.kata.checkout.domain.model.DiscountRule;
import com.kata.checkout.domain.model.PricedLine;
import com.kata.checkout.domain.model.Product;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscountEngineTest {

    private static final Instant VENCE = Instant.parse("2027-01-01T00:00:00Z");
    private static final Clock ANTES_DE_VENCER = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);

    private static final Product LAPTOP = new Product(1, "Laptop 14\"", "Tecnología", new BigDecimal("1200.00"));
    private static final Product LAMPARA = new Product(4, "Lámpara de escritorio", "Hogar", new BigDecimal("40.00"));

    @Test
    void aplicaLaCascadaCompletaDelEjemploDelContrato() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(
                List.of(new PricedLine(LAPTOP, 2), new PricedLine(LAMPARA, 1)), "WELCOME2026");

        assertThat(resultado.discounts()).extracting(AppliedDiscount::rule)
                .containsExactly(DiscountRule.CATEGORY, DiscountRule.VOLUME, DiscountRule.COUPON);
        paso(resultado, 0, "0.10", "Tecnología", "2400.00", "240.00");
        paso(resultado, 1, "0.05", "100.00", "2200.00", "110.00");
        paso(resultado, 2, "0.15", "WELCOME2026", "2090.00", "313.50");
        assertThat(resultado.subtotal()).isEqualTo(new BigDecimal("2440.00"));
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("663.50"));
        assertThat(resultado.total()).isEqualTo(new BigDecimal("1776.50"));
        assertThat(resultado.couponCode()).isEqualTo("WELCOME2026");
        assertThat(resultado.capApplied()).isFalse();
    }

    @Test
    void elDescuentoDeCategoriaSoloSeCalculaSobreLosProductosDeEsaCategoria() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(
                List.of(new PricedLine(precio("50.00", "Tecnología"), 1), new PricedLine(precio("30.00", "Hogar"), 1)),
                null);

        paso(resultado, 0, "0.10", "Tecnología", "50.00", "5.00");
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void unCarritoSinProductosDeLaCategoriaNoRegistraElPasoDeCategoria() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(List.of(new PricedLine(LAMPARA, 1)), null);

        assertThat(resultado.discounts()).isEmpty();
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(resultado.total()).isEqualTo(new BigDecimal("40.00"));
    }

    @Test
    void laCategoriaSeComparaEnUnicodeNormalizado() {
        // "Tecnología" con la tilde como carácter combinante (NFD)
        Product descompuesto = precio("50.00", "Tecnología");

        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(List.of(new PricedLine(descompuesto, 1)), null);

        assertThat(resultado.discounts()).extracting(AppliedDiscount::rule).containsExactly(DiscountRule.CATEGORY);
    }

    @Test
    void unSubtotalDeExactamenteCienNoRecibeElDescuentoPorVolumen() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(
                List.of(new PricedLine(precio("50.00", "Hogar"), 2)), null);

        assertThat(resultado.discounts()).isEmpty();
        assertThat(resultado.total()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void unSubtotalQueSuperaCienPorUnCentavoRecibeElDescuentoPorVolumen() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(
                List.of(new PricedLine(precio("100.01", "Hogar"), 1)), null);

        paso(resultado, 0, "0.05", "100.00", "100.01", "5.0005");
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("5.00"));
        assertThat(resultado.total()).isEqualTo(new BigDecimal("95.01"));
    }

    @Test
    void elUmbralDeVolumenSeEvaluaDespuesDelDescuentoDeCategoria() {
        // 110 de Tecnología quedan en 99 tras la categoría: ya no superan 100
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(
                List.of(new PricedLine(precio("110.00", "Tecnología"), 1)), null);

        assertThat(resultado.discounts()).extracting(AppliedDiscount::rule).containsExactly(DiscountRule.CATEGORY);
        assertThat(resultado.total()).isEqualTo(new BigDecimal("99.00"));
    }

    @Test
    void elCuponSeComparaIgnorandoMayusculasYEspaciosYDevuelveElCodigoConfigurado() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(List.of(new PricedLine(LAMPARA, 1)), "  welcome2026 ");

        paso(resultado, 0, "0.15", "WELCOME2026", "40.00", "6.00");
        assertThat(resultado.couponCode()).isEqualTo("WELCOME2026");
    }

    @Test
    void sinCuponNoHayPasoDeCuponNiCodigoAplicado() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(List.of(new PricedLine(LAMPARA, 1)), null);

        assertThat(resultado.couponCode()).isNull();
    }

    @Test
    void rechazaUnCuponNoRegistrado() {
        assertThatThrownBy(() -> motor(ANTES_DE_VENCER).apply(List.of(new PricedLine(LAMPARA, 1)), "BIENVENIDO"))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    void rechazaUnCuponEnBlancoComoNoRegistrado() {
        assertThatThrownBy(() -> motor(ANTES_DE_VENCER).apply(List.of(new PricedLine(LAMPARA, 1)), "   "))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    void rechazaElCuponEnElInstanteExactoEnQueVence() {
        Clock alVencer = Clock.fixed(VENCE, ZoneOffset.UTC);

        assertThatThrownBy(() -> motor(alVencer).apply(List.of(new PricedLine(LAMPARA, 1)), "WELCOME2026"))
                .isInstanceOf(CouponExpiredException.class);
    }

    @Test
    void aceptaElCuponUnSegundoAntesDeQueVenza() {
        Clock unSegundoAntes = Clock.fixed(VENCE.minusSeconds(1), ZoneOffset.UTC);

        DiscountResult resultado = motor(unSegundoAntes).apply(List.of(new PricedLine(LAMPARA, 1)), "WELCOME2026");

        assertThat(resultado.couponCode()).isEqualTo("WELCOME2026");
    }

    @Test
    void unCuponNoRegistradoSeRechazaAunqueElConfiguradoEsteVencido() {
        Clock vencido = Clock.fixed(VENCE.plusSeconds(1), ZoneOffset.UTC);

        assertThatThrownBy(() -> motor(vencido).apply(List.of(new PricedLine(LAMPARA, 1)), "OTRO"))
                .isInstanceOf(InvalidCouponException.class);
    }

    @Test
    void truncaElDescuentoExactamenteEnElTopeCuandoLaCascadaLoSupera() {
        // Categoría hipotética del 30%: 720 + 84 + 239.4 = 1043.4 (43.475%) > 840 (35% de 2400)
        DiscountEngine conCategoriaAlta = motor("0.30", "0.05", "0.15", "0.35", ANTES_DE_VENCER);

        DiscountResult resultado = conCategoriaAlta.apply(List.of(new PricedLine(LAPTOP, 2)), "WELCOME2026");

        assertThat(resultado.discounts()).extracting(AppliedDiscount::rule).containsExactly(
                DiscountRule.CATEGORY, DiscountRule.VOLUME, DiscountRule.COUPON, DiscountRule.CAP);
        paso(resultado, 3, "0.35", null, "1043.40", "203.40");
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("840.00"));
        assertThat(resultado.total()).isEqualTo(new BigDecimal("1560.00"));
        assertThat(resultado.capApplied()).isTrue();
    }

    @Test
    void unDescuentoIgualAlTopeNoSeRecortaNiRegistraElPasoDeTope() {
        DiscountEngine categoriaIgualAlTope = motor("0.35", "0.00", "0.15", "0.35", ANTES_DE_VENCER);

        DiscountResult resultado = categoriaIgualAlTope.apply(List.of(new PricedLine(LAPTOP, 2)), null);

        assertThat(resultado.discounts()).extracting(AppliedDiscount::rule).containsExactly(DiscountRule.CATEGORY);
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("840.00"));
        assertThat(resultado.capApplied()).isFalse();
    }

    @Test
    void conLosValoresActualesElDescuentoMaximoPosibleNoAlcanzaElTope() {
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(List.of(new PricedLine(LAPTOP, 1)), "WELCOME2026");

        // 1 - 0.90 × 0.95 × 0.85 = 27.325%
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("327.90"));
        assertThat(resultado.capApplied()).isFalse();
    }

    @Test
    void redondeaHalfEvenSoloLosTotalesYConservaLosPasosSinRedondear() {
        // 0.30 × 0.15 = 0.045: HALF_EVEN da 0.04 (HALF_UP daría 0.05)
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(
                List.of(new PricedLine(precio("0.30", "Papelería"), 1)), "WELCOME2026");

        assertThat(resultado.discounts().get(0).amount()).isEqualByComparingTo("0.045");
        assertThat(resultado.discounts().get(0).amount().scale()).isGreaterThan(2);
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("0.04"));
        assertThat(resultado.total()).isEqualTo(new BigDecimal("0.26"));
    }

    @Test
    void conElTopeAplicadoElDescuentoSeRedondeaHaciaAbajoParaNoSuperarElTreintaYCincoPorCiento() {
        // 100.10 × 0.40 = 40.04 > tope 35.035: HALF_EVEN daría 35.04 (por encima del 35%)
        DiscountEngine conCategoriaAlta = motor("0.40", "0.05", "0.15", "0.35", ANTES_DE_VENCER);

        DiscountResult resultado = conCategoriaAlta.apply(
                List.of(new PricedLine(precio("100.10", "Tecnología"), 1)), null);

        assertThat(resultado.capApplied()).isTrue();
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("35.03"));
        assertThat(resultado.total()).isEqualTo(new BigDecimal("65.07"));
        assertThat(resultado.discountTotal())
                .isLessThanOrEqualTo(new BigDecimal("100.10").multiply(new BigDecimal("0.35")));
    }

    @Test
    void sinTopeElDescuentoSigueRedondeandoseHalfEvenAunqueSubaElCentavo() {
        // 0.50 × 0.15 = 0.075: HALF_EVEN da 0.08 (DOWN daría 0.07)
        DiscountResult resultado = motor(ANTES_DE_VENCER).apply(
                List.of(new PricedLine(precio("0.50", "Papelería"), 1)), "WELCOME2026");

        assertThat(resultado.capApplied()).isFalse();
        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("0.08"));
        assertThat(resultado.total()).isEqualTo(new BigDecimal("0.42"));
    }

    @Test
    void unaReglaConTasaCeroNoRegistraUnPaso() {
        DiscountEngine sinCategoria = motor("0.00", "0.05", "0.15", "0.35", ANTES_DE_VENCER);

        DiscountResult resultado = sinCategoria.apply(List.of(new PricedLine(precio("50.00", "Tecnología"), 1)), null);

        assertThat(resultado.discounts()).isEmpty();
    }

    @Test
    void aplicaLasReglasEnElOrdenDeLaCascadaAunqueSeEntreguenDesordenadas() {
        DiscountEngine desordenado = new DiscountEngine(List.of(
                new CouponDiscount("WELCOME2026", new BigDecimal("0.15"), VENCE, ANTES_DE_VENCER),
                new VolumeDiscount(new BigDecimal("100.00"), new BigDecimal("0.05")),
                new CategoryDiscount("Tecnología", new BigDecimal("0.10"))),
                new BigDecimal("0.35"));

        DiscountResult resultado = desordenado.apply(
                List.of(new PricedLine(LAPTOP, 2), new PricedLine(LAMPARA, 1)), "WELCOME2026");

        assertThat(resultado.discountTotal()).isEqualTo(new BigDecimal("663.50"));
        assertThat(resultado.discounts()).extracting(AppliedDiscount::rule)
                .containsExactly(DiscountRule.CATEGORY, DiscountRule.VOLUME, DiscountRule.COUPON);
    }

    @Test
    void rechazaDosReglasDelMismoTipo() {
        List<DiscountStrategy> repetidas = List.of(
                new CategoryDiscount("Tecnología", new BigDecimal("0.10")),
                new CategoryDiscount("Hogar", new BigDecimal("0.10")));

        assertThatThrownBy(() -> new DiscountEngine(repetidas, new BigDecimal("0.35")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaUnTopeQueNoEsteEntreCeroExclusivoYUno() {
        assertThatThrownBy(() -> new DiscountEngine(List.of(), new BigDecimal("1.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscountEngine(List.of(), new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscountEngine(List.of(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaCalcularUnCarritoVacio() {
        assertThatThrownBy(() -> motor(ANTES_DE_VENCER).apply(List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DiscountEngine motor(Clock clock) {
        return motor("0.10", "0.05", "0.15", "0.35", clock);
    }

    private static DiscountEngine motor(String categoria, String volumen, String cupon, String tope, Clock clock) {
        return new DiscountEngine(List.of(
                new CategoryDiscount("Tecnología", new BigDecimal(categoria)),
                new VolumeDiscount(new BigDecimal("100.00"), new BigDecimal(volumen)),
                new CouponDiscount("WELCOME2026", new BigDecimal(cupon), VENCE, clock)),
                new BigDecimal(tope));
    }

    private static Product precio(String unitPrice, String category) {
        return new Product(99, "Producto de prueba", category, new BigDecimal(unitPrice));
    }

    private static void paso(DiscountResult resultado, int indice, String rate, String reference,
                             String baseAmount, String amount) {
        AppliedDiscount paso = resultado.discounts().get(indice);
        assertThat(paso.rate()).isEqualByComparingTo(rate);
        assertThat(paso.reference()).isEqualTo(reference);
        assertThat(paso.baseAmount()).isEqualByComparingTo(baseAmount);
        assertThat(paso.amount()).isEqualByComparingTo(amount);
    }
}
