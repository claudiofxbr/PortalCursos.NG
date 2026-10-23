package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.PixConfigCheck.Report;
import com.portalcursos.ng02.service.PixReconciliationService.Summary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/** Reconciliação automática do Pix: baixa o que o PagBank diz estar pago e sobrevive às falhas sem insistir. */
@ExtendWith(MockitoExtension.class)
class PixReconciliationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Mock private PaymentRepository paymentRepository;
    @Mock private PagBankPixGatewayService gateway;
    @Mock private PixConfigCheck configCheck;

    private PixReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new PixReconciliationService(paymentRepository, gateway, configCheck, true);
    }

    private static Payment pix(long id) {
        return Payment.builder().id(id).status(EPaymentStatus.PENDING).method(EPaymentMethod.PIX).pspOrderId("ORDE_" + id).build();
    }

    private void awaiting(Payment... payments) {
        when(paymentRepository.findPixAwaitingPayment(anyList(), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(List.of(payments));
    }

    @Test
    void baixaQuemOPagBankDizEstarPagoEDeixaOsDemais() {
        Payment paga = pix(1);
        Payment aguardando = pix(2);
        Payment semCobrancaAinda = pix(3);
        awaiting(paga, aguardando, semCobrancaAinda);
        when(gateway.fetchOrderStatus("ORDE_1")).thenReturn("PAID");
        when(gateway.fetchOrderStatus("ORDE_2")).thenReturn("WAITING");
        when(gateway.fetchOrderStatus("ORDE_3")).thenReturn(null);

        Summary summary = service.reconcileOnce(TODAY);

        assertEquals(new Summary(3, 1, 0, false), summary);
        assertEquals(EPaymentStatus.PAID, paga.getStatus());
        assertEquals(EPaymentStatus.PENDING, aguardando.getStatus());
        assertEquals(EPaymentStatus.PENDING, semCobrancaAinda.getStatus());
        verify(paymentRepository, times(1)).save(paga);
        verify(paymentRepository, never()).save(aguardando);
    }

    @Test
    void naoMudaOValorNemExcluiNada() {
        Payment paga = pix(1);
        paga.setAmount(new java.math.BigDecimal("175.00"));
        awaiting(paga);
        when(gateway.fetchOrderStatus("ORDE_1")).thenReturn("PAID");

        service.reconcileOnce(TODAY);

        assertEquals(0, paga.getAmount().compareTo(new java.math.BigDecimal("175.00")));
        assertTrue(paga.isActive());
    }

    @Test
    void semFaturasAguardandoNaoConsultaOPagBank() {
        awaiting();

        assertEquals(new Summary(0, 0, 0, false), service.reconcileOnce(TODAY));
        verifyNoInteractions(gateway);
    }

    @Test
    void usaAJanelaDeVencimentoComFolgaEPedeSoPendentesEVencidas() {
        awaiting();

        service.reconcileOnce(TODAY);

        verify(paymentRepository).findPixAwaitingPayment(
                eq(List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)),
                eq(TODAY.minusDays(PixReconciliationService.GRACE_DAYS_AFTER_DUE)),
                any(Pageable.class));
    }

    @Test
    void falhaDeUmaConsultaNaoImpedeAsDemais() {
        Payment a = pix(1);
        Payment b = pix(2);
        Payment c = pix(3);
        awaiting(a, b, c);
        when(gateway.fetchOrderStatus("ORDE_1")).thenThrow(new PaymentGatewayException("falha"));
        when(gateway.fetchOrderStatus("ORDE_2")).thenReturn("PAID");
        when(gateway.fetchOrderStatus("ORDE_3")).thenReturn("PAID");

        Summary summary = service.reconcileOnce(TODAY);

        assertEquals(new Summary(3, 2, 1, false), summary);
        assertEquals(EPaymentStatus.PENDING, a.getStatus());
        assertEquals(EPaymentStatus.PAID, b.getStatus());
        assertEquals(EPaymentStatus.PAID, c.getStatus());
    }

    @Test
    void tresFalhasSeguidasInterrompemARodadaSemInsistirNoPagBank() {
        Payment[] payments = {pix(1), pix(2), pix(3), pix(4), pix(5)};
        awaiting(payments);
        when(gateway.fetchOrderStatus(any())).thenThrow(new PaymentGatewayException("PagBank fora do ar"));

        Summary summary = service.reconcileOnce(TODAY);

        assertTrue(summary.aborted());
        assertEquals(3, summary.failed());
        assertEquals(3, summary.checked());
        verify(gateway, times(PixReconciliationService.ABORT_AFTER_CONSECUTIVE_FAILURES)).fetchOrderStatus(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void umaConsultaBoaZeraAContagemDeFalhasSeguidas() {
        Payment[] payments = {pix(1), pix(2), pix(3), pix(4), pix(5)};
        awaiting(payments);
        when(gateway.fetchOrderStatus("ORDE_1")).thenThrow(new PaymentGatewayException("x"));
        when(gateway.fetchOrderStatus("ORDE_2")).thenThrow(new PaymentGatewayException("x"));
        when(gateway.fetchOrderStatus("ORDE_3")).thenReturn("WAITING");
        when(gateway.fetchOrderStatus("ORDE_4")).thenThrow(new PaymentGatewayException("x"));
        when(gateway.fetchOrderStatus("ORDE_5")).thenReturn("PAID");

        Summary summary = service.reconcileOnce(TODAY);

        assertFalse(summary.aborted(), "as falhas não foram 3 seguidas");
        assertEquals(new Summary(5, 1, 3, false), summary);
    }

    @Test
    void conflitoDeVersaoComOWebhookNaoContaComoFalhaESegue() {
        Payment a = pix(1);
        Payment b = pix(2);
        awaiting(a, b);
        when(gateway.fetchOrderStatus(any())).thenReturn("PAID");
        when(paymentRepository.save(a)).thenThrow(new OptimisticLockingFailureException("baixada por outro processo"));

        Summary summary = service.reconcileOnce(TODAY);

        assertEquals(0, summary.failed());
        assertEquals(1, summary.paid(), "só a segunda foi baixada por esta rodada");
        verify(paymentRepository).save(b);
    }

    @Test
    void faturaJaPagaNaoEhSalvaDeNovo() {
        Payment jaPaga = pix(1);
        jaPaga.setStatus(EPaymentStatus.PAID);
        awaiting(jaPaga);
        when(gateway.fetchOrderStatus("ORDE_1")).thenReturn("PAID");

        assertEquals(0, service.reconcileOnce(TODAY).paid());
        verify(paymentRepository, never()).save(any());
    }

    // --- disparo agendado ---

    @Test
    void desligadoNaoFazNada() {
        PixReconciliationService off = new PixReconciliationService(paymentRepository, gateway, configCheck, false);

        off.scheduledRun();

        verifyNoInteractions(paymentRepository, gateway, configCheck);
    }

    @Test
    void pixNaoConfiguradoNaoConsultaNada() {
        when(configCheck.check()).thenReturn(new Report(PixConfigCheck.NAO_CONFIGURADO, "PRODUCAO", List.of()));

        service.scheduledRun();

        verifyNoInteractions(paymentRepository, gateway);
    }

    @Test
    void configuradoRodaAReconciliacao() {
        when(configCheck.check()).thenReturn(new Report(PixConfigCheck.OK, "PRODUCAO", List.of()));
        Payment paga = pix(1);
        awaiting(paga);
        when(gateway.fetchOrderStatus("ORDE_1")).thenReturn("PAID");

        service.scheduledRun();

        assertEquals(EPaymentStatus.PAID, paga.getStatus());
    }

    @Test
    void erroInesperadoNaRodadaNaoDerrubaOAgendador() {
        when(configCheck.check()).thenReturn(new Report(PixConfigCheck.OK, "SANDBOX", List.of()));
        when(paymentRepository.findPixAwaitingPayment(anyList(), any(LocalDate.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("banco indisponível"));

        service.scheduledRun();   // não pode lançar
    }
}
