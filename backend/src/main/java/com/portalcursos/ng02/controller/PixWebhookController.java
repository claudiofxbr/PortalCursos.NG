package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.PagBankPixGatewayService;
import com.portalcursos.ng02.service.PaymentCodeGenerationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Recebe notificações assíncronas de pagamento do PagBank para pedidos PIX. Sem
 * {@code @PreAuthorize}/JWT de propósito — é chamado pelo PSP, não por um usuário
 * autenticado do sistema; a autenticidade da chamada é validada via HMAC no próprio
 * path (ver {@link #verifyToken}), não por sessão.
 *
 * <p>O {@code paymentId} vai no path (junto do token) em vez de o servidor iterar
 * todos os pagamentos tentando bater o HMAC — é a abordagem recomendada pela tarefa:
 * mais simples e eficiente (lookup direto por id + comparação de um único HMAC).
 */
@RestController
@RequestMapping("/api/finance/pix/webhook")
@RequiredArgsConstructor
public class PixWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(PixWebhookController.class);

    private final PaymentRepository paymentRepository;
    private final PaymentCodeGenerationService paymentCodeGenerationService;
    private final PagBankPixGatewayService pagBankPixGatewayService;

    @PostMapping("/{paymentId}/{webhookToken}")
    public ResponseEntity<?> receiveNotification(
            @PathVariable Long paymentId,
            @PathVariable String webhookToken,
            @RequestBody(required = false) Map<String, Object> payload) {

        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);
        if (paymentOpt.isEmpty() || !verifyToken(paymentId, webhookToken)) {
            // 404 genérico em ambos os casos (id inexistente ou token inválido) para não
            // revelar se o id existe a quem não tem o token correto.
            logger.warn("[PIX WEBHOOK] Notificação recusada: paymentId={} não encontrado ou token inválido", paymentId);
            return ResponseEntity.notFound().build();
        }

        Payment payment = paymentOpt.get();
        if (payment.getPspOrderId() == null) {
            logger.warn("[PIX WEBHOOK] Notificação para paymentId={} sem pspOrderId associado", paymentId);
            return ResponseEntity.notFound().build();
        }

        // Nunca confia no corpo do POST recebido (pode ser forjado por qualquer um que
        // adivinhe/vaze o token) — sempre revalida com uma chamada autenticada nossa ao PagBank.
        try {
            String status = pagBankPixGatewayService.fetchOrderStatus(payment.getPspOrderId());
            if ("PAID".equals(status) && payment.getStatus() != EPaymentStatus.PAID) {
                payment.setStatus(EPaymentStatus.PAID);
                paymentRepository.save(payment);
                logger.info("[PIX WEBHOOK] Pagamento confirmado via PagBank: paymentId={}", paymentId);
            }
        } catch (PaymentGatewayException ex) {
            // Log e retorno 200 mesmo assim: evita que o PagBank fique retentando
            // indefinidamente por um erro do nosso lado que não vai se resolver sozinho
            // (ex: token mal configurado) — a reconciliação pode ser feita depois por outro
            // meio (consulta manual/job). Se preferíssemos retentativa automática do PSP,
            // devolveríamos 503 aqui; optamos por 200 para não gerar tempestade de retries.
            logger.warn("[PIX WEBHOOK] Falha ao reconciliar status junto ao PagBank para paymentId={}", paymentId);
        }

        return ResponseEntity.ok().build();
    }

    private boolean verifyToken(Long paymentId, String webhookToken) {
        String expected = paymentCodeGenerationService.computeWebhookToken(paymentId);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                webhookToken.getBytes(StandardCharsets.UTF_8));
    }
}
