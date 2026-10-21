package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.dto.CardPaymentFacts;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.CardPaymentReleaseService.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

@ExtendWith(MockitoExtension.class)
class CardPaymentReleaseServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private CardPaymentReleaseService service;

    private Payment pending() {
        return Payment.builder()
                .id(42L)
                .amount(new BigDecimal("199.90"))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(3))
                .pspCheckoutId("CHEC_ABC")
                .chargedAmountCents(19990L)
                .build();
    }

    private CardPaymentFacts facts(String ref, boolean paid, Long cents, String type) {
        return new CardPaymentFacts(ref, paid, cents, type);
    }

    @Test
    void confirmadoComReferenciaEValorIguaisBaixaComoCredito() {
        Payment p = pending();

        assertEquals(Outcome.GRANTED, service.release(p, facts("PORTAL-CARD-42", true, 19990L, "CREDIT_CARD")));

        assertEquals(EPaymentStatus.PAID, p.getStatus());
        assertEquals(EPaymentMethod.CREDIT_CARD, p.getMethod());
        verify(paymentRepository).save(p);
    }

    @Test
    void cobrancaDeDebitoGravaMetodoDebito() {
        Payment p = pending();
        service.release(p, facts("PORTAL-CARD-42", true, 19990L, "DEBIT_CARD"));
        assertEquals(EPaymentMethod.DEBIT_CARD, p.getMethod());
    }

    @Test
    void meioDesconhecidoCaiEmCredito() {
        Payment p = pending();
        service.release(p, facts("PORTAL-CARD-42", true, 19990L, null));
        assertEquals(EPaymentMethod.CREDIT_CARD, p.getMethod());
    }

    @Test
    void jaPagaEIdempotenteENaoSalvaDeNovo() {
        Payment p = pending();
        p.setStatus(EPaymentStatus.PAID);

        assertEquals(Outcome.ALREADY, service.release(p, facts("PORTAL-CARD-42", true, 19990L, "CREDIT_CARD")));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void naoPagoOuSemFatosNaoBaixa() {
        assertEquals(Outcome.NOT_PAID, service.release(pending(), null));
        assertEquals(Outcome.NOT_PAID, service.release(pending(), facts("PORTAL-CARD-42", false, null, null)));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void valorDiferenteReferenciaDiferenteOuValorDesconhecidoNaoBaixam() {
        Payment p = pending();
        assertEquals(Outcome.MISMATCH, service.release(p, facts("PORTAL-CARD-42", true, 100L, "CREDIT_CARD")));
        assertEquals(Outcome.MISMATCH, service.release(p, facts("PORTAL-CARD-99", true, 19990L, "CREDIT_CARD")));
        assertEquals(Outcome.MISMATCH, service.release(p, facts("PORTAL-CARD-42", true, null, "CREDIT_CARD")));
        assertEquals(EPaymentStatus.PENDING, p.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void faturaSemValorFixadoNaoBaixa() {
        Payment p = pending();
        p.setChargedAmountCents(null);
        assertEquals(Outcome.MISMATCH, service.release(p, facts("PORTAL-CARD-42", true, 19990L, "CREDIT_CARD")));
    }

    @Test
    void faturaCanceladaNaoBaixaSozinha() {
        Payment p = pending();
        p.setStatus(EPaymentStatus.CANCELLED);

        assertEquals(Outcome.INVOICE_CANCELLED, service.release(p, facts("PORTAL-CARD-42", true, 19990L, "CREDIT_CARD")));
        assertEquals(EPaymentStatus.CANCELLED, p.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void faturaVencidaPagaComOValorFixadoNaCriacaoBaixa() {
        Payment p = pending();
        p.setStatus(EPaymentStatus.OVERDUE);
        p.setDueDate(LocalDate.now().minusDays(30)); // total de hoje diferente do valor fixado
        assertNotEquals(19990L, PagBankCheckoutGatewayService.amountInCents(p));

        assertEquals(Outcome.GRANTED, service.release(p, facts("PORTAL-CARD-42", true, 19990L, "CREDIT_CARD")));
    }
}
