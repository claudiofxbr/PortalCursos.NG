package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.CardPaymentFacts;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.CardPaymentReleaseService;
import com.portalcursos.ng02.service.CardPaymentReleaseService.Outcome;
import com.portalcursos.ng02.service.PagBankCheckoutGatewayService;
import com.portalcursos.ng02.service.PaymentCodeGenerationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Notificações do PagBank para checkouts de cartão. Sem JWT de propósito (quem chama é o PSP); a chamada é
 * autenticada pelo HMAC no caminho (paymentId + token), o mesmo esquema do Pix.
 *
 * <p>O corpo do POST é IGNORADO por completo: a verdade vem de uma reconsulta autenticada nossa ao PagBank
 * usando o id de checkout que nós mesmos gravamos na fatura. Quem adivinhar/vazar a URL no máximo faz o
 * servidor consultar o PagBank; nunca consegue baixar fatura.
 *
 * <p>Respostas: 404 genérico para id/token inválidos; 503 em falha temporária (o PagBank reenvia); 200 para o
 * resto (inclusive divergência de valor/referência, que é registrada em log e não deve gerar tempestade de retries).
 */
@RestController
@RequestMapping("/api/finance/card/webhook")
@RequiredArgsConstructor
public class CardWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(CardWebhookController.class);

    private final PaymentRepository paymentRepository;
    private final PaymentCodeGenerationService paymentCodeGenerationService;
    private final PagBankCheckoutGatewayService checkoutGateway;
    private final CardPaymentReleaseService releaseService;

    @PostMapping("/{paymentId}/{webhookToken}")
    public ResponseEntity<?> receiveNotification(
            @PathVariable Long paymentId,
            @PathVariable String webhookToken,
            @org.springframework.web.bind.annotation.RequestBody(required = false) String ignoredBody) {

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !verifyToken(paymentId, webhookToken) || payment.getPspCheckoutId() == null) {
            logger.warn("[CARD WEBHOOK] Notificação recusada: paymentId={} não encontrado, sem checkout ou token inválido", paymentId);
            return ResponseEntity.notFound().build();
        }

        try {
            CardPaymentFacts facts = checkoutGateway.resolveCheckoutFacts(payment.getPspCheckoutId());
            Outcome outcome = releaseService.release(payment, facts);
            switch (outcome) {
                case GRANTED -> logger.info("[CARD WEBHOOK] Pagamento confirmado via PagBank: paymentId={}", paymentId);
                case MISMATCH -> logger.error("[CARD WEBHOOK] Referência ou valor divergente do esperado: paymentId={}", paymentId);
                default -> { }
            }
            return outcome == Outcome.MISMATCH || outcome == Outcome.INVOICE_CANCELLED
                    ? ResponseEntity.ok(Map.of("ok", true, "ignored", true))
                    : ResponseEntity.ok(Map.of("ok", true));
        } catch (PaymentGatewayException | OptimisticLockingFailureException ex) {
            // Falha temporária (PSP fora do ar, concorrência): 503 para o PagBank reenviar depois.
            logger.warn("[CARD WEBHOOK] Falha temporária ao processar paymentId={}: {}", paymentId, ex.getClass().getSimpleName());
            return ResponseEntity.status(503).body(Map.of("error", "TEMPORARY_FAILURE"));
        }
    }

    private boolean verifyToken(Long paymentId, String webhookToken) {
        String expected = paymentCodeGenerationService.computeWebhookToken(paymentId);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                webhookToken.getBytes(StandardCharsets.UTF_8));
    }
}
