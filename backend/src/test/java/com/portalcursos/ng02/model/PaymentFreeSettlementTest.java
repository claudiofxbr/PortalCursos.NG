package com.portalcursos.ng02.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/** Fatura de valor zero (procedimento gratuito) nasce quitada; as demais não são tocadas. */
class PaymentFreeSettlementTest {

    private static Payment payment(EPaymentStatus status, BigDecimal amount) {
        return Payment.builder().status(status).amount(amount).build();
    }

    @Test
    void faturaPendenteDeValorZeroFicaPaga() {
        Payment p = payment(EPaymentStatus.PENDING, BigDecimal.ZERO);

        assertTrue(p.settleIfFree());
        assertEquals(EPaymentStatus.PAID, p.getStatus());
    }

    @Test
    void zeroComEscalaDiferenteTambemContaComoZero() {
        Payment p = payment(EPaymentStatus.PENDING, new BigDecimal("0.00"));

        assertTrue(p.settleIfFree());
        assertEquals(EPaymentStatus.PAID, p.getStatus());
    }

    @Test
    void umCentavoContinuaPendente() {
        Payment p = payment(EPaymentStatus.PENDING, new BigDecimal("0.01"));

        assertFalse(p.settleIfFree());
        assertEquals(EPaymentStatus.PENDING, p.getStatus());
    }

    @Test
    void valorAusenteNaoQuita() {
        Payment p = payment(EPaymentStatus.PENDING, null);

        assertFalse(p.settleIfFree());
        assertEquals(EPaymentStatus.PENDING, p.getStatus());
    }

    @Test
    void faturaCanceladaOuVencidaDeValorZeroNaoMudaDeSituacao() {
        for (EPaymentStatus status : new EPaymentStatus[] {EPaymentStatus.CANCELLED, EPaymentStatus.OVERDUE}) {
            Payment p = payment(status, BigDecimal.ZERO);

            assertFalse(p.settleIfFree());
            assertEquals(status, p.getStatus());
        }
    }

    @Test
    void faturaJaPagaNaoEhReportadaComoQuitadaAgora() {
        Payment p = payment(EPaymentStatus.PAID, BigDecimal.ZERO);

        assertFalse(p.settleIfFree());
        assertEquals(EPaymentStatus.PAID, p.getStatus());
    }
}
