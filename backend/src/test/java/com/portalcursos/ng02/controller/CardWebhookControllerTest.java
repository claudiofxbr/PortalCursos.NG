package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.CardPaymentFacts;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.CardPaymentReleaseService;
import com.portalcursos.ng02.service.CardPaymentReleaseService.Outcome;
import com.portalcursos.ng02.service.PagBankCheckoutGatewayService;
import com.portalcursos.ng02.service.PaymentCodeGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** O corpo do POST nunca é fonte da verdade; tudo vem da reconsulta (mockada) ao PagBank. */
@ExtendWith(MockitoExtension.class)
class CardWebhookControllerTest {

    private static final Long ID = 42L;
    private static final String TOKEN = "valid-hmac-token";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentCodeGenerationService paymentCodeGenerationService;

    @Mock
    private PagBankCheckoutGatewayService checkoutGateway;

    @Mock
    private CardPaymentReleaseService releaseService;

    @InjectMocks
    private CardWebhookController controller;

    private Payment withCheckout() {
        return Payment.builder()
                .id(ID)
                .amount(new BigDecimal("199.90"))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(3))
                .pspCheckoutId("CHEC_ABC")
                .chargedAmountCents(19990L)
                .build();
    }

    @Test
    void tokenValidoReconsultaEBaixa() {
        Payment p = withCheckout();
        CardPaymentFacts facts = new CardPaymentFacts("PORTAL-CARD-42", true, 19990L, "CREDIT_CARD");
        when(paymentRepository.findById(ID)).thenReturn(Optional.of(p));
        when(paymentCodeGenerationService.computeWebhookToken(ID)).thenReturn(TOKEN);
        when(checkoutGateway.resolveCheckoutFacts("CHEC_ABC")).thenReturn(facts);
        when(releaseService.release(p, facts)).thenReturn(Outcome.GRANTED);

        ResponseEntity<?> r = controller.receiveNotification(ID, TOKEN, "{\"id\":\"CHEC_FORJADO\",\"status\":\"PAID\"}");

        assertEquals(HttpStatus.OK, r.getStatusCode());
        // usa o checkout gravado na fatura, nunca o id do corpo
        verify(checkoutGateway).resolveCheckoutFacts("CHEC_ABC");
        verify(checkoutGateway, never()).resolveCheckoutFacts("CHEC_FORJADO");
    }

    @Test
    void tokenInvalidoOuFaturaInexistenteRetorna404SemConsultarOPsp() {
        when(paymentRepository.findById(ID)).thenReturn(Optional.of(withCheckout()));
        when(paymentCodeGenerationService.computeWebhookToken(ID)).thenReturn(TOKEN);
        assertEquals(HttpStatus.NOT_FOUND, controller.receiveNotification(ID, "errado", null).getStatusCode());

        when(paymentRepository.findById(7L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, controller.receiveNotification(7L, TOKEN, null).getStatusCode());

        verifyNoInteractions(checkoutGateway, releaseService);
    }

    @Test
    void faturaSemCheckoutGravadoRetorna404() {
        Payment p = withCheckout();
        p.setPspCheckoutId(null);
        when(paymentRepository.findById(ID)).thenReturn(Optional.of(p));
        when(paymentCodeGenerationService.computeWebhookToken(ID)).thenReturn(TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, controller.receiveNotification(ID, TOKEN, null).getStatusCode());
        verifyNoInteractions(checkoutGateway, releaseService);
    }

    @Test
    void falhaDoPspRetorna503ParaOPagBankReenviar() {
        when(paymentRepository.findById(ID)).thenReturn(Optional.of(withCheckout()));
        when(paymentCodeGenerationService.computeWebhookToken(ID)).thenReturn(TOKEN);
        when(checkoutGateway.resolveCheckoutFacts(any())).thenThrow(new PaymentGatewayException("falha"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.receiveNotification(ID, TOKEN, null).getStatusCode());
        verifyNoInteractions(releaseService);
    }

    @Test
    void conflitoDeConcorrenciaRetorna503() {
        Payment p = withCheckout();
        when(paymentRepository.findById(ID)).thenReturn(Optional.of(p));
        when(paymentCodeGenerationService.computeWebhookToken(ID)).thenReturn(TOKEN);
        when(checkoutGateway.resolveCheckoutFacts(any())).thenReturn(null);
        when(releaseService.release(p, null)).thenThrow(new OptimisticLockingFailureException("v"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, controller.receiveNotification(ID, TOKEN, null).getStatusCode());
    }

    @Test
    void divergenciaOuNaoPagoRespondem200SemRetentativa() {
        Payment p = withCheckout();
        when(paymentRepository.findById(ID)).thenReturn(Optional.of(p));
        when(paymentCodeGenerationService.computeWebhookToken(ID)).thenReturn(TOKEN);
        when(checkoutGateway.resolveCheckoutFacts(any())).thenReturn(null);
        when(releaseService.release(p, null)).thenReturn(Outcome.MISMATCH, Outcome.NOT_PAID, Outcome.ALREADY);

        for (int i = 0; i < 3; i++) {
            assertEquals(HttpStatus.OK, controller.receiveNotification(ID, TOKEN, null).getStatusCode());
        }
    }
}
