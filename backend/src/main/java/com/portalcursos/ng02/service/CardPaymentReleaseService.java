package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.CardPaymentFacts;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ÚNICO ponto de baixa de fatura paga por cartão. Só baixa se, nesta ordem: a API do PagBank confirmou
 * cobrança PAID; o reference_id devolvido é o desta fatura; e o valor pago em centavos é exatamente o
 * valor fixado na criação do checkout ({@code charged_amount_cents}). Idempotente por status; concorrência
 * entre duas notificações é resolvida pelo {@code @Version} da fatura (a perdedora é reenviada pelo PagBank
 * e então encontra a fatura já paga).
 */
@Service
@RequiredArgsConstructor
public class CardPaymentReleaseService {

    public enum Outcome { GRANTED, ALREADY, NOT_PAID, MISMATCH, INVOICE_CANCELLED }

    private static final Logger logger = LoggerFactory.getLogger(CardPaymentReleaseService.class);

    private final PaymentRepository paymentRepository;

    @Transactional
    public Outcome release(Payment payment, CardPaymentFacts facts) {
        if (payment.getStatus() == EPaymentStatus.PAID) {
            return Outcome.ALREADY;
        }
        if (facts == null || !facts.paid()) {
            return Outcome.NOT_PAID;
        }
        if (!PagBankCheckoutGatewayService.referenceFor(payment.getId()).equals(facts.referenceId())
                || payment.getChargedAmountCents() == null
                || facts.paidAmountCents() == null
                || !payment.getChargedAmountCents().equals(facts.paidAmountCents())) {
            return Outcome.MISMATCH;
        }
        if (payment.getStatus() == EPaymentStatus.CANCELLED) {
            // Dinheiro recebido numa fatura cancelada: não baixa sozinho, exige análise manual.
            logger.error("[CARD WEBHOOK] Pagamento confirmado em fatura CANCELADA (paymentId={}); revisão manual", payment.getId());
            return Outcome.INVOICE_CANCELLED;
        }

        payment.setStatus(EPaymentStatus.PAID);
        payment.setMethod("DEBIT_CARD".equals(facts.methodType()) ? EPaymentMethod.DEBIT_CARD : EPaymentMethod.CREDIT_CARD);
        paymentRepository.save(payment);
        return Outcome.GRANTED;
    }
}
