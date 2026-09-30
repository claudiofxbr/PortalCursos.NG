package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.PagBankPixGatewayService;
import com.portalcursos.ng02.service.PaymentCodeGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobertura do PixWebhookController — chamado pelo PagBank, sem autenticação de
 * sessão. O ponto de segurança central testado aqui: o resultado final NUNCA confia no
 * corpo do POST recebido (poderia ser forjado por qualquer um que soubesse a URL),
 * sempre revalida via GET autenticado nosso ao PagBank (mockado, nunca chamada real).
 */
@ExtendWith(MockitoExtension.class)
class PixWebhookControllerTest {

    private static final Long PAYMENT_ID = 42L;
    private static final String VALID_TOKEN = "valid-hmac-token";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCodeGenerationService paymentCodeGenerationService;

    @Mock
    private PagBankPixGatewayService pagBankPixGatewayService;

    @InjectMocks
    private PixWebhookController controller;

    private Payment pendingPaymentWithOrder() {
        return Payment.builder()
                .id(PAYMENT_ID)
                .amount(new BigDecimal("199.90"))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.of(2026, 12, 1))
                .pspOrderId("ORDE_ABC123")
                .build();
    }

    @Test
    void tokenValidoEStatusPagoNoGetConfirmaPagamento() {
        Payment payment = pendingPaymentWithOrder();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentCodeGenerationService.computeWebhookToken(PAYMENT_ID)).thenReturn(VALID_TOKEN);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ABC123")).thenReturn("PAID");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.receiveNotification(
                PAYMENT_ID, VALID_TOKEN, Map.of("status", "PAID"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EPaymentStatus.PAID, payment.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void ignoraStatusDoCorpoDoPostEUsaApenasOResultadoDoGetDeReconciliacao() {
        // Corpo do POST mente dizendo "PAID", mas a reconciliação via GET (a fonte de
        // verdade) diz que ainda está aguardando pagamento — o resultado final deve
        // seguir o GET, não o corpo da notificação.
        Payment payment = pendingPaymentWithOrder();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentCodeGenerationService.computeWebhookToken(PAYMENT_ID)).thenReturn(VALID_TOKEN);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ABC123")).thenReturn("WAITING");

        ResponseEntity<?> response = controller.receiveNotification(
                PAYMENT_ID, VALID_TOKEN, Map.of("status", "PAID", "charges", "forged"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EPaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void tokenInvalidoRetorna404SemAlterarPagamento() {
        Payment payment = pendingPaymentWithOrder();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentCodeGenerationService.computeWebhookToken(PAYMENT_ID)).thenReturn(VALID_TOKEN);

        ResponseEntity<?> response = controller.receiveNotification(
                PAYMENT_ID, "token-forjado-invalido", Map.of("status", "PAID"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(EPaymentStatus.PENDING, payment.getStatus());
        verifyNoInteractions(pagBankPixGatewayService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paymentIdInexistenteRetorna404() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.receiveNotification(PAYMENT_ID, VALID_TOKEN, Map.of());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(pagBankPixGatewayService);
    }

    @Test
    void falhaAoReconciliarComPagBankRetorna200SemAlterarPagamentoEmVezDePropagarErro() {
        Payment payment = pendingPaymentWithOrder();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentCodeGenerationService.computeWebhookToken(PAYMENT_ID)).thenReturn(VALID_TOKEN);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ABC123"))
                .thenThrow(new PaymentGatewayException("Falha ao comunicar com o gateway de pagamento"));

        ResponseEntity<?> response = controller.receiveNotification(PAYMENT_ID, VALID_TOKEN, Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EPaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(any());
    }
}
