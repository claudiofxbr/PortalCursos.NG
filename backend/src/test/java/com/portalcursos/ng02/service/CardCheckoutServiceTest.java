package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.dto.CardCheckoutResponse;
import com.portalcursos.ng02.dto.CardCheckoutResult;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class CardCheckoutServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PagBankCheckoutGatewayService checkoutGateway;

    @Mock
    private PaymentCodeGenerationService paymentCodeGenerationService;

    @InjectMocks
    private CardCheckoutService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "publicApiUrl", "https://api.example.test");
        ReflectionTestUtils.setField(service, "publicAppUrl", "https://app.example.test/portalcursos.ng");
        ReflectionTestUtils.setField(service, "debitEnabled", false);
    }

    private Payment pending(long id, String amount) {
        return Payment.builder()
                .id(id)
                .amount(new BigDecimal(amount))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(5))
                .build();
    }

    @Test
    void criaCheckoutGravaCamposEMontaUrls() {
        Payment p = pending(42L, "199.90");
        when(paymentCodeGenerationService.computeWebhookToken(42L)).thenReturn("tok");
        when(checkoutGateway.createCardCheckout(eq(p), any(), any(), anyBoolean()))
                .thenReturn(new CardCheckoutResult("CHEC_ABC", "https://pay.example/p", 19990L));

        CardCheckoutResponse r = service.generate(p);

        assertEquals("https://pay.example/p", r.url());
        assertEquals(42L, r.paymentId());
        assertFalse(r.reused());
        assertEquals(EPaymentMethod.CREDIT_CARD, p.getMethod());
        assertEquals("CHEC_ABC", p.getPspCheckoutId());
        assertEquals(19990L, p.getChargedAmountCents());
        assertEquals("https://pay.example/p", p.getPaymentCode());
        verify(paymentRepository).save(p);

        ArgumentCaptor<String> redirect = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> notify = ArgumentCaptor.forClass(String.class);
        verify(checkoutGateway).createCardCheckout(eq(p), redirect.capture(), notify.capture(), eq(false));
        assertEquals("https://app.example.test/portalcursos.ng/finance?payment=42", redirect.getValue());
        assertEquals("https://api.example.test/api/finance/card/webhook/42/tok", notify.getValue());
    }

    @Test
    void debitoSoEOferecidoQuandoHabilitado() {
        ReflectionTestUtils.setField(service, "debitEnabled", true);
        Payment p = pending(1L, "10.00");
        when(paymentCodeGenerationService.computeWebhookToken(1L)).thenReturn("t");
        when(checkoutGateway.createCardCheckout(eq(p), any(), any(), eq(true)))
                .thenReturn(new CardCheckoutResult("CHEC_X1", "https://pay.example/p", 1000L));

        service.generate(p);

        verify(checkoutGateway).createCardCheckout(eq(p), any(), any(), eq(true));
    }

    @Test
    void faturaPagaOuCanceladaNaoGeraCheckout() {
        Payment paid = pending(1L, "10.00");
        paid.setStatus(EPaymentStatus.PAID);
        Payment cancelled = pending(2L, "10.00");
        cancelled.setStatus(EPaymentStatus.CANCELLED);

        assertThrows(BusinessException.class, () -> service.generate(paid));
        assertThrows(BusinessException.class, () -> service.generate(cancelled));
        verifyNoInteractions(checkoutGateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void faturaVencidaPodeGerarCheckout() {
        Payment p = pending(3L, "10.00");
        p.setStatus(EPaymentStatus.OVERDUE);
        when(paymentCodeGenerationService.computeWebhookToken(3L)).thenReturn("t");
        when(checkoutGateway.createCardCheckout(eq(p), any(), any(), anyBoolean()))
                .thenReturn(new CardCheckoutResult("CHEC_X3", "https://pay.example/p", 1000L));

        assertFalse(service.generate(p).reused());
    }

    private Payment withRecentCheckout(long cents) {
        Payment p = pending(5L, "100.00");
        p.setMethod(EPaymentMethod.CREDIT_CARD);
        p.setPspCheckoutId("CHEC_OLD");
        p.setChargedAmountCents(cents);
        p.setPaymentCode("https://pay.example/old");
        p.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        return p;
    }

    @Test
    void reaproveitaCheckoutRecenteComMesmoValor() {
        Payment p = withRecentCheckout(10000L);

        CardCheckoutResponse r = service.generate(p);

        assertTrue(r.reused());
        assertEquals("https://pay.example/old", r.url());
        verifyNoInteractions(checkoutGateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void naoReaproveitaQuandoValorMudouOuCheckoutAntigoOuSemCampos() {
        when(paymentCodeGenerationService.computeWebhookToken(5L)).thenReturn("t");
        when(checkoutGateway.createCardCheckout(any(), any(), any(), anyBoolean()))
                .thenReturn(new CardCheckoutResult("CHEC_NEW", "https://pay.example/new", 10000L));

        Payment changed = withRecentCheckout(9999L);
        assertFalse(service.generate(changed).reused());

        Payment old = withRecentCheckout(10000L);
        old.setUpdatedAt(LocalDateTime.now().minusMinutes(120));
        assertFalse(service.generate(old).reused());

        Payment pixNow = withRecentCheckout(10000L);
        pixNow.setMethod(EPaymentMethod.PIX);
        assertFalse(service.generate(pixNow).reused());

        Payment httpUrl = withRecentCheckout(10000L);
        httpUrl.setPaymentCode("00020126580014BR.GOV.BCB.PIX");
        assertFalse(service.generate(httpUrl).reused());

        verify(checkoutGateway, times(4)).createCardCheckout(any(), any(), any(), anyBoolean());
    }

    @Test
    void falhaDoGatewayNaoAlteraAFatura() {
        Payment p = pending(7L, "10.00");
        when(paymentCodeGenerationService.computeWebhookToken(7L)).thenReturn("t");
        when(checkoutGateway.createCardCheckout(any(), any(), any(), anyBoolean()))
                .thenThrow(new PaymentGatewayException("falha"));

        assertThrows(PaymentGatewayException.class, () -> service.generate(p));

        assertNull(p.getPspCheckoutId());
        assertNull(p.getChargedAmountCents());
        assertNull(p.getMethod());
        verify(paymentRepository, never()).save(any());
    }
}
