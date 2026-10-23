package com.portalcursos.ng02.service;

import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Baixa automática de Pix pago, sem depender do webhook nem de ninguém: a cada poucos minutos consulta o PagBank sobre
 * as faturas com Pix gerado e ainda pendentes e quita as que já foram pagas. É a rede de segurança para quando o
 * webhook não chega (servidor reiniciando num deploy, falha de rede, erro temporário).
 *
 * <p>Seguro por construção: só <b>lê</b> o PagBank e só muda a situação para PAGA (nunca valor, nunca exclui); é
 * idempotente (concorrência com o webhook ou com outra instância é resolvida pelo {@code @Version} da fatura); falha
 * de uma consulta não impede as demais; e se o PagBank estiver fora do ar a rodada para sozinha.
 */
@Service
public class PixReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(PixReconciliationService.class);

    /** Quantas faturas no máximo por rodada. */
    static final int BATCH_SIZE = 200;
    /** Falhas seguidas no PagBank que interrompem a rodada (provavelmente fora do ar: não adianta insistir). */
    static final int ABORT_AFTER_CONSECUTIVE_FAILURES = 3;
    /** O Pix vale até o vencimento + 1 dia; a folga cobre pagamento confirmado com atraso pelo PagBank. */
    static final int GRACE_DAYS_AFTER_DUE = 3;

    /** Resultado de uma rodada. */
    public record Summary(int checked, int paid, int failed, boolean aborted) {
    }

    private final PaymentRepository paymentRepository;
    private final PagBankPixGatewayService gateway;
    private final PixConfigCheck configCheck;
    private final boolean enabled;

    public PixReconciliationService(PaymentRepository paymentRepository,
                                    PagBankPixGatewayService gateway,
                                    PixConfigCheck configCheck,
                                    @Value("${portalcursos.pix.reconcile-enabled:true}") boolean enabled) {
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.configCheck = configCheck;
        this.enabled = enabled;
    }

    /** Disparo agendado. Não faz nada se estiver desligado ou se o Pix não estiver configurado. */
    @Scheduled(fixedDelayString = "${portalcursos.pix.reconcile-interval-ms:300000}",
            initialDelayString = "${portalcursos.pix.reconcile-initial-delay-ms:90000}")
    public void scheduledRun() {
        if (!enabled) {
            return;
        }
        if (PixConfigCheck.NAO_CONFIGURADO.equals(configCheck.check().status())) {
            logger.debug("[PIX-RECON] Pix não configurado; nada a reconciliar.");
            return;
        }
        try {
            Summary summary = reconcileOnce(LocalDate.now());
            if (summary.checked() > 0 || summary.aborted()) {
                logger.info("[PIX-RECON] verificadas={} baixadas={} falhas={} interrompida={}",
                        summary.checked(), summary.paid(), summary.failed(), summary.aborted());
            }
        } catch (RuntimeException ex) {
            // Nunca derruba o agendador: o erro vai para o log e a próxima rodada tenta de novo.
            logger.warn("[PIX-RECON] Rodada falhou: {}", ex.getClass().getSimpleName());
        }
    }

    /** Uma rodada de reconciliação, com "hoje" explícito para facilitar o teste. */
    public Summary reconcileOnce(LocalDate today) {
        List<Payment> awaiting = paymentRepository.findPixAwaitingPayment(
                List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE),
                today.minusDays(GRACE_DAYS_AFTER_DUE), PageRequest.of(0, BATCH_SIZE));

        int checked = 0;
        int paid = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        for (Payment payment : awaiting) {
            checked++;
            try {
                String status = gateway.fetchOrderStatus(payment.getPspOrderId());
                consecutiveFailures = 0;
                if ("PAID".equals(status) && payment.getStatus() != EPaymentStatus.PAID) {
                    payment.setStatus(EPaymentStatus.PAID);
                    paymentRepository.save(payment);
                    paid++;
                    logger.info("[PIX-RECON] fatura {} baixada: o PagBank informa o Pix como pago", payment.getId());
                }
            } catch (OptimisticLockingFailureException ex) {
                // Outra instância ou o webhook baixou a mesma fatura agora: já está resolvido.
                logger.debug("[PIX-RECON] fatura {} já atualizada por outro processo", payment.getId());
            } catch (PaymentGatewayException ex) {
                failed++;
                consecutiveFailures++;
                if (consecutiveFailures >= ABORT_AFTER_CONSECUTIVE_FAILURES) {
                    logger.warn("[PIX-RECON] {} falhas seguidas ao consultar o PagBank; rodada interrompida", consecutiveFailures);
                    return new Summary(checked, paid, failed, true);
                }
            }
        }
        return new Summary(checked, paid, failed, false);
    }
}
