package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.PixGenerationResponse;
import com.portalcursos.ng02.dto.PixOrderResult;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

/**
 * Geração de código de pagamento (PIX real via PagBank / boleto simulado) — extraída de
 * {@code FinancialController} (Lote E, item F3 da auditoria).
 *
 * <p>Busca da fatura (404) e checagem de ownership (403) continuam no controller —
 * mesmo critério usado nos demais itens do Lote E: aqui fica só a ação de domínio
 * (gerar o código e persistir), o controller decide o que fazer antes de chamá-la.
 */
@Service
@RequiredArgsConstructor
public class PaymentCodeGenerationService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PaymentRepository paymentRepository;
    private final PagBankPixGatewayService pagBankPixGatewayService;

    @Value("${pagseguro.webhook.secret}")
    private String webhookSecret;

    @Value("${portalcursos.public-api-url}")
    private String publicApiUrl;

    /**
     * Gera um PIX real via PagBank e persiste o pedido na fatura. Falha (400) se a
     * fatura já estiver paga; falha (502, via {@link com.portalcursos.ng02.exception.PaymentGatewayException})
     * se o gateway não puder ser contatado.
     */
    public PixGenerationResponse generatePix(Payment payment) {
        if (payment.getStatus() == EPaymentStatus.PAID) {
            throw new BusinessException("Fatura já paga, não é possível gerar novo PIX");
        }

        String webhookToken = computeWebhookToken(payment.getId());
        String webhookUrl = publicApiUrl + "/api/finance/pix/webhook/" + payment.getId() + "/" + webhookToken;

        PixOrderResult result = pagBankPixGatewayService.createPixOrder(payment, webhookToken, webhookUrl);

        payment.setMethod(EPaymentMethod.PIX);
        payment.setPaymentCode(result.copiaECola());
        payment.setPspOrderId(result.pspOrderId());
        Payment saved = paymentRepository.save(payment);

        return new PixGenerationResponse(saved, result.qrCodeImageUrl());
    }

    /**
     * HMAC-SHA256 determinístico de {@code paymentId + ":" + secret}, hex-encoded — usado
     * como token de URL do webhook. Determinístico (não um UUID aleatório persistido) para
     * que {@code PixWebhookController} possa recalcular e comparar sem precisar de coluna
     * nova no banco.
     */
    public String computeWebhookToken(Long paymentId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new PaymentGatewayException("Gateway de pagamento não configurado");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal((paymentId + ":" + webhookSecret).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(rawHmac.length * 2);
            for (byte b : rawHmac) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Falha ao calcular token de webhook", ex);
        }
    }

    public Payment generateBoleto(Payment payment) {
        payment.setMethod(EPaymentMethod.BOLETO);
        payment.setPaymentCode(buildSimulatedBoletoUrl(payment));
        return paymentRepository.save(payment);
    }

    /**
     * SIMULAÇÃO — o boleto continua fora de escopo desta integração (só o PIX foi
     * substituído por gateway real). Não altere sem pedido explícito.
     */
    private String buildSimulatedBoletoUrl(Payment p) {
        return "https://portalcursos.edu.br/financeiro/boletos/download/SIM-" + p.getId()
                + "-" + p.getDueDate();
    }
}
