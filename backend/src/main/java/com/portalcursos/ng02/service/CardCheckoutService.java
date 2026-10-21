package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.CardCheckoutResponse;
import com.portalcursos.ng02.dto.CardCheckoutResult;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Criação do checkout de cartão (crédito/débito) de uma fatura. Segue o critério do Lote E: busca da
 * fatura (404), dono (403), trava de sandbox e limite de tentativas ficam no controller; aqui fica a
 * ação de domínio. O Pix ({@link PaymentCodeGenerationService}) não é tocado.
 */
@Service
@RequiredArgsConstructor
public class CardCheckoutService {

    /** O link PAY do PagBank expira em ~2 h; reaproveitar só enquanto bem dentro desse prazo. */
    static final Duration REUSE_WINDOW = Duration.ofMinutes(90);

    private final PaymentRepository paymentRepository;
    private final PagBankCheckoutGatewayService checkoutGateway;
    private final PaymentCodeGenerationService paymentCodeGenerationService;

    @Value("${portalcursos.public-api-url}")
    private String publicApiUrl;

    @Value("${portalcursos.public-app-url:http://localhost:3000}")
    private String publicAppUrl;

    /** Débito só é oferecido depois que o PagBank aprova o método na conta. */
    @Value("${pagbank.debit-enabled:false}")
    private boolean debitEnabled;

    public CardCheckoutResponse generate(Payment payment) {
        if (payment.getStatus() == EPaymentStatus.PAID) {
            throw new BusinessException("Fatura já paga, não é possível gerar cobrança por cartão");
        }
        if (payment.getStatus() == EPaymentStatus.CANCELLED) {
            throw new BusinessException("Fatura cancelada, não é possível gerar cobrança por cartão");
        }

        if (canReuse(payment)) {
            return new CardCheckoutResponse(payment.getPaymentCode(), payment.getId(), true);
        }

        String webhookToken = paymentCodeGenerationService.computeWebhookToken(payment.getId());
        String notificationUrl = publicApiUrl + "/api/finance/card/webhook/" + payment.getId() + "/" + webhookToken;
        String redirectUrl = publicAppUrl + "/finance?payment=" + payment.getId();

        CardCheckoutResult result = checkoutGateway.createCardCheckout(payment, redirectUrl, notificationUrl, debitEnabled);

        // Método provisório: o webhook grava crédito ou débito conforme o que foi de fato pago.
        payment.setMethod(EPaymentMethod.CREDIT_CARD);
        payment.setPspCheckoutId(result.checkoutId());
        payment.setChargedAmountCents(result.chargedAmountCents());
        payment.setPaymentCode(result.payUrl());
        paymentRepository.save(payment);

        return new CardCheckoutResponse(result.payUrl(), payment.getId(), false);
    }

    /**
     * Reaproveita o checkout da fatura só se for de cartão, tiver link https, o valor cobrado ainda for
     * o total atual (juros de fatura vencida mudam por dia) e tiver sido gravado há menos de 90 min.
     * Limitação conhecida: o prazo é contado pela última alteração da fatura (não há coluna própria),
     * então uma edição posterior só estende a janela; o PagBank continua sendo a barreira (link vencido
     * apenas leva a uma página de checkout expirado, sem baixa indevida).
     */
    private boolean canReuse(Payment payment) {
        String url = payment.getPaymentCode();
        LocalDateTime updatedAt = payment.getUpdatedAt();
        return payment.getMethod() == EPaymentMethod.CREDIT_CARD
                && payment.getPspCheckoutId() != null
                && url != null && url.startsWith("https://")
                && payment.getChargedAmountCents() != null
                && payment.getChargedAmountCents() == PagBankCheckoutGatewayService.amountInCents(payment)
                && updatedAt != null
                && updatedAt.isAfter(LocalDateTime.now().minus(REUSE_WINDOW));
    }
}
