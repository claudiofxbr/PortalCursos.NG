package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.dto.PixGenerationResponse;
import com.portalcursos.ng02.dto.PixOrderResult;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cobertura do Lote E, item F3: PaymentCodeGenerationService extraído de
 * FinancialController — agora com PIX real via PagBank (o cliente HTTP é sempre
 * mockado por {@link PagBankPixGatewayService}, nunca chamado de verdade aqui).
 */
@ExtendWith(MockitoExtension.class)
public class PaymentCodeGenerationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PagBankPixGatewayService pagBankPixGatewayService;

    @InjectMocks
    private PaymentCodeGenerationService paymentCodeGenerationService;

    @BeforeEach
    void setUpConfig() {
        ReflectionTestUtils.setField(paymentCodeGenerationService, "webhookSecret", "test-webhook-secret");
        ReflectionTestUtils.setField(paymentCodeGenerationService, "publicApiUrl", "https://portalcursos.example.test");
    }

    private Payment pendingPayment(long id) {
        return Payment.builder()
                .id(id)
                .amount(new BigDecimal("199.90"))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.of(2026, 12, 1))
                .build();
    }

    @Test
    public void generatePixComSucessoDefinePaymentCodePspOrderIdMetodoERetornaQrCode() {
        Payment payment = pendingPayment(42L);
        PixOrderResult gatewayResult = new PixOrderResult(
                "ORDE_ABC123", "00020126580014BR.GOV.BCB.PIX...", "https://pagbank.example/qr.png");
        when(pagBankPixGatewayService.createPixOrder(eq(payment), any(), any())).thenReturn(gatewayResult);
        when(paymentRepository.save(payment)).thenReturn(payment);

        PixGenerationResponse response = paymentCodeGenerationService.generatePix(payment);

        assertEquals(EPaymentMethod.PIX, response.payment().getMethod());
        assertEquals("00020126580014BR.GOV.BCB.PIX...", response.payment().getPaymentCode());
        assertEquals("ORDE_ABC123", response.payment().getPspOrderId());
        assertEquals("https://pagbank.example/qr.png", response.qrCodeImageUrl());
        verify(paymentRepository).save(payment);
    }

    @Test
    public void generatePixQuandoFaturaJaPagaLancaBusinessExceptionSemChamarGateway() {
        Payment payment = pendingPayment(1L);
        payment.setStatus(EPaymentStatus.PAID);

        assertThrows(BusinessException.class, () -> paymentCodeGenerationService.generatePix(payment));

        verifyNoInteractions(pagBankPixGatewayService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void generatePixQuandoGatewayFalhaPropagaPaymentGatewayExceptionSemPersistir() {
        Payment payment = pendingPayment(7L);
        when(pagBankPixGatewayService.createPixOrder(eq(payment), any(), any()))
                .thenThrow(new PaymentGatewayException("Falha ao comunicar com o gateway de pagamento"));

        assertThrows(PaymentGatewayException.class, () -> paymentCodeGenerationService.generatePix(payment));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void computeWebhookTokenEDeterministicoParaOMesmoPaymentId() {
        String token1 = paymentCodeGenerationService.computeWebhookToken(42L);
        String token2 = paymentCodeGenerationService.computeWebhookToken(42L);
        String tokenOutro = paymentCodeGenerationService.computeWebhookToken(43L);

        assertEquals(token1, token2);
        assertNotEquals(token1, tokenOutro);
    }

    @Test
    public void computeWebhookTokenComWebhookSecretVazioLancaPaymentGatewayExceptionSemCalcularHmac() {
        ReflectionTestUtils.setField(paymentCodeGenerationService, "webhookSecret", "");

        assertThrows(PaymentGatewayException.class,
                () -> paymentCodeGenerationService.computeWebhookToken(42L));
    }

    @Test
    public void generatePixComWebhookSecretVazioLancaPaymentGatewayExceptionSemChamarGateway() {
        ReflectionTestUtils.setField(paymentCodeGenerationService, "webhookSecret", "   ");
        Payment payment = pendingPayment(9L);

        assertThrows(PaymentGatewayException.class, () -> paymentCodeGenerationService.generatePix(payment));

        verifyNoInteractions(pagBankPixGatewayService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void generateBoletoDefineMetodoEUrlEPersiste() {
        Payment payment = Payment.builder()
                .id(7L)
                .amount(new BigDecimal("500.00"))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.of(2026, 11, 15))
                .build();
        when(paymentRepository.save(payment)).thenReturn(payment);

        Payment result = paymentCodeGenerationService.generateBoleto(payment);

        assertEquals(EPaymentMethod.BOLETO, result.getMethod());
        assertEquals("https://portalcursos.edu.br/financeiro/boletos/download/SIM-7-2026-11-15",
                result.getPaymentCode());
        verify(paymentRepository).save(payment);
    }
}
