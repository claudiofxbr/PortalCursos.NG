package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Reconciliação do Pix com banco H2 e repositório reais (só o PagBank é simulado): a consulta escolhe exatamente as
 * faturas certas e a baixa fica gravada no banco.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:pixreconciliationtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
class PixReconciliationIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Autowired private PixReconciliationService service;
    @Autowired private PaymentRepository paymentRepository;
    @MockitoBean private PagBankPixGatewayService gateway;

    private Payment save(EPaymentMethod method, String psp, EPaymentStatus status, LocalDate due) {
        return paymentRepository.saveAndFlush(Payment.builder().amount(new BigDecimal("100.00")).dueDate(due)
                .status(status).method(method).pspOrderId(psp).build());
    }

    @Test
    void consultaSoAsFaturasComPixPendenteDentroDaJanelaEBaixaAsPagas() {
        Payment alvoPago = save(EPaymentMethod.PIX, "ORDE_PAGO", EPaymentStatus.PENDING, TODAY.plusDays(5));
        Payment alvoAguardando = save(EPaymentMethod.PIX, "ORDE_AGUARDA", EPaymentStatus.OVERDUE, TODAY.minusDays(1));
        save(EPaymentMethod.PIX, "ORDE_JA_BAIXADA", EPaymentStatus.PAID, TODAY.plusDays(5));
        save(EPaymentMethod.BOLETO, "ORDE_BOLETO", EPaymentStatus.PENDING, TODAY.plusDays(5));
        save(EPaymentMethod.PIX, null, EPaymentStatus.PENDING, TODAY.plusDays(5));
        save(EPaymentMethod.PIX, "ORDE_MUITO_ANTIGA", EPaymentStatus.PENDING, TODAY.minusDays(30));
        save(EPaymentMethod.PIX, "ORDE_CANCELADA", EPaymentStatus.CANCELLED, TODAY.plusDays(5));
        when(gateway.fetchOrderStatus(anyString())).thenReturn("WAITING");
        when(gateway.fetchOrderStatus("ORDE_PAGO")).thenReturn("PAID");

        PixReconciliationService.Summary summary = service.reconcileOnce(TODAY);

        assertEquals(2, summary.checked(), "só as 2 faturas com Pix pendente na janela são consultadas");
        assertEquals(1, summary.paid());
        assertEquals(EPaymentStatus.PAID, paymentRepository.findById(alvoPago.getId()).orElseThrow().getStatus());
        assertEquals(EPaymentStatus.OVERDUE, paymentRepository.findById(alvoAguardando.getId()).orElseThrow().getStatus());
    }

    @Test
    void segundaRodadaNaoRefazNada() {
        save(EPaymentMethod.PIX, "ORDE_X", EPaymentStatus.PENDING, TODAY.plusDays(5));
        when(gateway.fetchOrderStatus("ORDE_X")).thenReturn("PAID");

        assertEquals(1, service.reconcileOnce(TODAY).paid());
        PixReconciliationService.Summary second = service.reconcileOnce(TODAY);

        assertEquals(0, second.checked(), "depois de baixada a fatura sai da consulta");
        assertEquals(0, second.paid());
    }
}
