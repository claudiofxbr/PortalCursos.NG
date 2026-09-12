package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cobertura do Lote E, item F3: PaymentCodeGenerationService extraído de
 * FinancialController (geração simulada de PIX/boleto).
 */
@ExtendWith(MockitoExtension.class)
public class PaymentCodeGenerationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentCodeGenerationService paymentCodeGenerationService;

    @Test
    public void generatePixDefineMetodoECodigoEPersiste() {
        Payment payment = Payment.builder()
                .id(42L)
                .amount(new BigDecimal("199.90"))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.of(2026, 12, 1))
                .build();
        when(paymentRepository.save(payment)).thenReturn(payment);

        Payment result = paymentCodeGenerationService.generatePix(payment);

        assertEquals(EPaymentMethod.PIX, result.getMethod());
        assertNotNull(result.getPaymentCode());
        assertTrue(result.getPaymentCode().contains("PORTAL0000000042"));
        assertTrue(result.getPaymentCode().startsWith("00020126580014BR.GOV.BCB.PIX0136"));
        verify(paymentRepository).save(payment);
    }

    @Test
    public void generatePixComValorNuloUsaZeroSemLancarErro() {
        Payment payment = Payment.builder().id(1L).status(EPaymentStatus.PENDING).build();
        when(paymentRepository.save(payment)).thenReturn(payment);

        Payment result = paymentCodeGenerationService.generatePix(payment);

        assertNotNull(result.getPaymentCode());
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

    @Test
    public void codigosPixDeFaturasDiferentesSaoDiferentes() {
        Payment p1 = Payment.builder().id(1L).amount(new BigDecimal("100.00")).status(EPaymentStatus.PENDING).build();
        Payment p2 = Payment.builder().id(2L).amount(new BigDecimal("100.00")).status(EPaymentStatus.PENDING).build();
        when(paymentRepository.save(p1)).thenReturn(p1);
        when(paymentRepository.save(p2)).thenReturn(p2);

        String code1 = paymentCodeGenerationService.generatePix(p1).getPaymentCode();
        String code2 = paymentCodeGenerationService.generatePix(p2).getPaymentCode();

        assertNotEquals(code1, code2);
    }
}
