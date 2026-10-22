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

    // --- Fatura sem valor a pagar ---

    @Test
    public void faturaDeValorZeroNaoGeraPixNemChamaOGateway() {
        Payment gratuita = pendingPayment(30L);
        gratuita.setAmount(BigDecimal.ZERO);

        BusinessException ex = assertThrows(BusinessException.class, () -> paymentCodeGenerationService.generatePix(gratuita));

        assertTrue(ex.getMessage().contains("valor zero"));
        verifyNoInteractions(pagBankPixGatewayService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void faturaSemValorDefinidoNaoGeraPix() {
        Payment semValor = pendingPayment(31L);
        semValor.setAmount(null);

        assertThrows(BusinessException.class, () -> paymentCodeGenerationService.generatePix(semValor));
        verifyNoInteractions(pagBankPixGatewayService);
    }

    @Test
    public void faturaDeUmCentavoAindaGeraPix() {
        Payment umCentavo = pendingPayment(32L);
        umCentavo.setAmount(new BigDecimal("0.01"));
        when(pagBankPixGatewayService.createPixOrder(eq(umCentavo), any(), any()))
                .thenReturn(new PixOrderResult("ORDE_1C", "CODIGO-1C", null));
        when(paymentRepository.save(umCentavo)).thenReturn(umCentavo);

        assertEquals("ORDE_1C", paymentCodeGenerationService.generatePix(umCentavo).payment().getPspOrderId());
    }

    // --- Pix já existente: não pode ser sobrescrito por um novo pedido ---

    private Payment withLivePix(long id) {
        Payment payment = pendingPayment(id);
        payment.setDueDate(LocalDate.now().plusDays(10));
        payment.setMethod(EPaymentMethod.PIX);
        payment.setPspOrderId("ORDE_ORIGINAL");
        payment.setPaymentCode("CODIGO-ORIGINAL");
        return payment;
    }

    @Test
    public void pedirPixDeNovoComPixAguardandoPagamentoReaproveitaOMesmoCodigoSemCriarOutroPedido() {
        Payment payment = withLivePix(10L);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ORIGINAL")).thenReturn("WAITING");

        PixGenerationResponse response = paymentCodeGenerationService.generatePix(payment);

        assertEquals("ORDE_ORIGINAL", response.payment().getPspOrderId());
        assertEquals("CODIGO-ORIGINAL", response.payment().getPaymentCode());
        assertNull(response.qrCodeImageUrl());
        verify(pagBankPixGatewayService, never()).createPixOrder(any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void pedirPixDeNovoQuandoPagBankAindaNaoTemCobrancaReaproveita() {
        // Pedido Pix recém-criado: a API ainda não lista cobrança paga, então o status vem nulo.
        Payment payment = withLivePix(11L);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ORIGINAL")).thenReturn(null);

        assertEquals("CODIGO-ORIGINAL", paymentCodeGenerationService.generatePix(payment).payment().getPaymentCode());
        verify(pagBankPixGatewayService, never()).createPixOrder(any(), any(), any());
    }

    @Test
    public void pedirPixDeNovoQuandoOPixAnteriorJaFoiPagoBaixaAFaturaENaoCriaOutro() {
        Payment payment = withLivePix(12L);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ORIGINAL")).thenReturn("PAID");
        when(paymentRepository.save(payment)).thenReturn(payment);

        assertThrows(BusinessException.class, () -> paymentCodeGenerationService.generatePix(payment));

        assertEquals(EPaymentStatus.PAID, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(pagBankPixGatewayService, never()).createPixOrder(any(), any(), any());
    }

    @Test
    public void pedirPixDeNovoComFalhaNaConsultaAoPagBankReaproveitaEmVezDeCriarOutro() {
        Payment payment = withLivePix(13L);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ORIGINAL"))
                .thenThrow(new PaymentGatewayException("PagBank fora do ar"));

        PixGenerationResponse response = paymentCodeGenerationService.generatePix(payment);

        assertEquals("ORDE_ORIGINAL", response.payment().getPspOrderId());
        verify(pagBankPixGatewayService, never()).createPixOrder(any(), any(), any());
    }

    @Test
    public void pedirPixDeNovoComPedidoAnteriorCanceladoCriaUmNovoESobrescreve() {
        Payment payment = withLivePix(14L);
        when(pagBankPixGatewayService.fetchOrderStatus("ORDE_ORIGINAL")).thenReturn("CANCELED");
        when(pagBankPixGatewayService.createPixOrder(eq(payment), any(), any()))
                .thenReturn(new PixOrderResult("ORDE_NOVO", "CODIGO-NOVO", null));
        when(paymentRepository.save(payment)).thenReturn(payment);

        PixGenerationResponse response = paymentCodeGenerationService.generatePix(payment);

        assertEquals("ORDE_NOVO", response.payment().getPspOrderId());
        assertEquals("CODIGO-NOVO", response.payment().getPaymentCode());
    }

    @Test
    public void pedirPixDeNovoComPixExpiradoCriaUmNovoSemConsultarOAntigo() {
        Payment payment = withLivePix(15L);
        payment.setDueDate(LocalDate.now().minusDays(3));
        when(pagBankPixGatewayService.createPixOrder(eq(payment), any(), any()))
                .thenReturn(new PixOrderResult("ORDE_NOVO", "CODIGO-NOVO", null));
        when(paymentRepository.save(payment)).thenReturn(payment);

        assertEquals("ORDE_NOVO", paymentCodeGenerationService.generatePix(payment).payment().getPspOrderId());
        verify(pagBankPixGatewayService, never()).fetchOrderStatus(any());
    }

    @Test
    public void faturaComBoletoOuSemCodigoAindaGeraPixNormalmente() {
        Payment boleto = pendingPayment(16L);
        boleto.setDueDate(LocalDate.now().plusDays(10));
        boleto.setMethod(EPaymentMethod.BOLETO);
        boleto.setPspOrderId("ORDE_QUALQUER");
        boleto.setPaymentCode("https://boleto");
        when(pagBankPixGatewayService.createPixOrder(eq(boleto), any(), any()))
                .thenReturn(new PixOrderResult("ORDE_NOVO", "CODIGO-NOVO", null));
        when(paymentRepository.save(boleto)).thenReturn(boleto);

        assertEquals(EPaymentMethod.PIX, paymentCodeGenerationService.generatePix(boleto).payment().getMethod());
        verify(pagBankPixGatewayService, never()).fetchOrderStatus(any());
    }
}
