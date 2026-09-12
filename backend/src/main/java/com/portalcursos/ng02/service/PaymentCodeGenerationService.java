package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Geração simulada de código de pagamento (PIX/boleto) — extraída de
 * {@code FinancialController} (Lote E, item F3 da auditoria). Puro código movido, sem
 * mudança de comportamento: mesma lógica de geração, mesmo formato de código.
 *
 * <p>Busca da fatura (404) e checagem de ownership (403) continuam no controller —
 * mesmo critério usado nos demais itens do Lote E: aqui fica só a ação de domínio
 * (gerar o código e persistir), o controller decide o que fazer antes de chamá-la.
 */
@Service
@RequiredArgsConstructor
public class PaymentCodeGenerationService {

    private final PaymentRepository paymentRepository;

    public Payment generatePix(Payment payment) {
        payment.setMethod(EPaymentMethod.PIX);
        payment.setPaymentCode(buildSimulatedPixCode(payment));
        return paymentRepository.save(payment);
    }

    public Payment generateBoleto(Payment payment) {
        payment.setMethod(EPaymentMethod.BOLETO);
        payment.setPaymentCode(buildSimulatedBoletoUrl(payment));
        return paymentRepository.save(payment);
    }

    /**
     * SIMULAÇÃO — não há integração com um PSP (gateway de pagamento) real.
     * O código gerado varia por fatura (id, valor e vencimento) para evitar que
     * todas as cobranças recebam o mesmo "QR Code", mas não é um payload BR Code
     * válido para uso bancário real. Substituir por integração real (ex: Mercado
     * Pago, PagSeguro) antes de processar pagamentos de produção.
     */
    private String buildSimulatedPixCode(Payment p) {
        BigDecimal amount = p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO;
        String amountDigits = amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .toBigInteger()
                .toString();
        String txid = String.format("PORTAL%010d", p.getId());
        // "%08s" era inválido (o flag '0' não existe para conversão %s em Java — lança
        // FormatFlagsConversionMismatchException) e fazia generatePix falhar sempre com 500,
        // nunca coberto por teste até a extração para este service. "%8s" + replace faz o
        // zero-padding pretendido (espaço à esquerda -> '0').
        return "00020126580014BR.GOV.BCB.PIX0136" + txid
                + "5204000053039865802BR5913PortalCursos6008BRASILIA62070503***6304"
                + String.format("%8s", amountDigits).replace(" ", "0");
    }

    private String buildSimulatedBoletoUrl(Payment p) {
        return "https://portalcursos.edu.br/financeiro/boletos/download/SIM-" + p.getId()
                + "-" + p.getDueDate();
    }
}
