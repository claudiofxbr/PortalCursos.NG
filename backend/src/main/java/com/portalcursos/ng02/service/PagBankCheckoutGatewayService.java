package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.CardCheckoutResult;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cliente do Checkout hospedado do PagBank para cartão de crédito e débito (o Pix continua em
 * {@link PagBankPixGatewayService}, que não é tocado). O cliente paga na página do PagBank, então
 * nenhum dado de cartão passa pelo PortalCursos.NG.
 *
 * <p>Contrato (POST {@code {baseUrl}/checkouts}), conforme confirmado no sandbox do CVFacil.NG:
 * <ul>
 *   <li>{@code payment_methods} é lista de OBJETOS {@code {"type": ...}}; lista de textos dá HTTP 400;</li>
 *   <li>{@code customer} pode ser omitido (o cliente preenche na página do PagBank);</li>
 *   <li>a resposta traz {@code id} ("CHEC_...") e {@code links[]} com {@code rel = "PAY"};</li>
 *   <li>{@code payment_notification_urls} e {@code notification_urls} recebem o mesmo webhook.</li>
 * </ul>
 * Débito exige aprovação interna do PagBank na conta; enquanto não houver, ele não é oferecido.
 * Valor sempre em centavos inteiros, vindo da fatura no servidor. Token e corpo de erro do PSP nunca vão a log.
 */
@Service
public class PagBankCheckoutGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(PagBankCheckoutGatewayService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    // psp_checkout_id é VARCHAR(64)
    private static final Pattern CHECKOUT_ID = Pattern.compile("^[A-Za-z0-9_-]{4,64}$");

    private final String apiToken;
    private final RestClient restClient;

    public PagBankCheckoutGatewayService(
            @Value("${pagseguro.api.token}") String apiToken,
            @Value("${pagseguro.api.base-url}") String baseUrl) {
        this.apiToken = apiToken;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** Referência do checkout no PagBank; o webhook a recalcula a partir da fatura para conferir. */
    public static String referenceFor(Long paymentId) {
        return "PORTAL-CARD-" + paymentId;
    }

    /**
     * Cria o checkout de cartão da fatura.
     *
     * @param redirectUrl     para onde o PagBank devolve o aluno depois do pagamento (https)
     * @param notificationUrl webhook completo (já com paymentId e token)
     * @param includeDebit    false enquanto o débito não estiver aprovado na conta PagBank
     */
    @SuppressWarnings("unchecked")
    public CardCheckoutResult createCardCheckout(
            Payment payment, String redirectUrl, String notificationUrl, boolean includeDebit) {
        requireConfiguredToken();

        long amountInCents = payment.getTotalAmount()
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
        if (amountInCents <= 0) {
            throw new PaymentGatewayException("Valor da fatura inválido para cobrança");
        }

        String name = payment.getDescription() != null ? payment.getDescription() : "Mensalidade PortalCursos";

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("reference_id", "PORTAL-ITEM-" + payment.getId());
        item.put("name", name);
        item.put("quantity", 1);
        item.put("unit_amount", amountInCents);

        List<Map<String, String>> methods = includeDebit
                ? List.of(Map.of("type", "CREDIT_CARD"), Map.of("type", "DEBIT_CARD"))
                : List.of(Map.of("type", "CREDIT_CARD"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reference_id", referenceFor(payment.getId()));
        body.put("items", List.of(item));
        body.put("payment_methods", methods);
        body.put("redirect_url", redirectUrl);
        body.put("return_url", redirectUrl);
        body.put("payment_notification_urls", List.of(notificationUrl));
        body.put("notification_urls", List.of(notificationUrl));

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/checkouts")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            logger.warn("[PAGBANK] Falha ao criar checkout de cartão (paymentId={})", payment.getId());
            logger.debug("[PAGBANK] Detalhe técnico da falha de criação de checkout", ex);
            throw new PaymentGatewayException("Falha ao comunicar com o gateway de pagamento", ex);
        }

        if (response == null) {
            throw new PaymentGatewayException("Resposta vazia do gateway de pagamento");
        }

        Object idValue = response.get("id");
        String checkoutId = idValue instanceof String s ? s : "";
        String payUrl = extractPayUrl(response.get("links"));
        if (!CHECKOUT_ID.matcher(checkoutId).matches() || !isSafeHttpsUrl(payUrl)) {
            throw new PaymentGatewayException("Resposta inesperada do gateway de pagamento");
        }
        return new CardCheckoutResult(checkoutId, payUrl, amountInCents);
    }

    private String extractPayUrl(Object links) {
        if (!(links instanceof List<?> list)) {
            return "";
        }
        for (Object link : list) {
            if (link instanceof Map<?, ?> m && "PAY".equals(m.get("rel")) && m.get("href") instanceof String href) {
                return href;
            }
        }
        return "";
    }

    private boolean isSafeHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void requireConfiguredToken() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new PaymentGatewayException("Gateway de pagamento não configurado");
        }
    }
}
