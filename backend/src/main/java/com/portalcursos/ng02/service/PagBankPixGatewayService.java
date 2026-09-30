package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.PixOrderResult;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente da API de Pedidos/Cobranças PIX do PagBank/PagSeguro (integração direta —
 * o PortalCursos.NG recebe na própria conta, não é o fluxo OAuth Connect de
 * marketplace).
 *
 * <p><b>Suposições de contrato assumidas nesta implementação</b> (sem acesso à
 * documentação ao vivo do PagBank no momento desta tarefa — precisam ser validadas
 * contra a conta sandbox real antes de ir para produção):
 *
 * <p><b>POST {@code {baseUrl}/orders}</b> — corpo de requisição:
 * <pre>{@code
 * {
 *   "reference_id": "PORTAL-<paymentId>",
 *   "customer": { "name": "...", "email": "...", "tax_id": "<cpf só dígitos>" },
 *   "items": [{ "name": "...", "quantity": 1, "unit_amount": <centavos inteiro> }],
 *   "qr_codes": [{ "amount": { "value": <centavos> }, "expiration_date": "<ISO-8601 instant>" }],
 *   "notification_urls": ["<PORTALCURSOS_PUBLIC_API_URL>/api/finance/pix/webhook/<paymentId>/<webhookToken>"]
 * }
 * }</pre>
 * Resposta esperada:
 * <pre>{@code
 * {
 *   "id": "ORDE_XXXX",
 *   "qr_codes": [{ "id": "QRCO_XXX", "text": "<BR Code copia-e-cola>",
 *                   "links": [{ "rel": "QRCODE.PNG", "href": "https://..." }] }]
 * }
 * }</pre>
 *
 * <p><b>GET {@code {baseUrl}/orders/{pspOrderId}}</b> — resposta esperada:
 * <pre>{@code
 * { "charges": [{ "status": "PAID" | "DECLINED" | "WAITING" | ... }] }
 * }</pre>
 * Se {@code charges} vier vazio/nulo, interpretamos como "ainda não pago" (sem lançar erro) —
 * é o estado esperado logo após a criação do pedido, antes do pagamento ser efetuado.
 */
@Service
public class PagBankPixGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(PagBankPixGatewayService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String apiToken;
    private final RestClient restClient;

    public PagBankPixGatewayService(
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

    /**
     * Cria um pedido/cobrança PIX no PagBank para a fatura informada.
     *
     * @param payment      fatura para a qual o PIX está sendo gerado (deve ter {@link Student} carregado)
     * @param webhookToken token HMAC determinístico usado para reconciliação assíncrona via webhook
     * @param webhookUrl   URL completa de notificação (já monta {@code paymentId}/{@code webhookToken})
     */
    public PixOrderResult createPixOrder(Payment payment, String webhookToken, String webhookUrl) {
        requireConfiguredToken();

        Student student = payment.getStudent();
        long amountInCents = payment.getTotalAmount()
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();

        String expirationDate = payment.getDueDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS)
                .toString();

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("name", student != null ? student.getFullName() : "Aluno PortalCursos");
        if (student != null && student.getEmail() != null) {
            customer.put("email", student.getEmail());
        }
        if (student != null && student.getCpf() != null) {
            customer.put("tax_id", student.getCpf().replaceAll("\\D", ""));
        }

        Map<String, Object> item = Map.of(
                "name", payment.getDescription() != null ? payment.getDescription() : "Mensalidade PortalCursos",
                "quantity", 1,
                "unit_amount", amountInCents
        );

        Map<String, Object> qrCode = Map.of(
                "amount", Map.of("value", amountInCents),
                "expiration_date", expirationDate
        );

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("reference_id", "PORTAL-" + payment.getId());
        requestBody.put("customer", customer);
        requestBody.put("items", List.of(item));
        requestBody.put("qr_codes", List.of(qrCode));
        requestBody.put("notification_urls", List.of(webhookUrl));

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            // Nunca logar o token nem o corpo bruto da resposta de erro (pode conter dado
            // sensível do PSP) em nível que vá para log de produção; detalhe técnico fica em DEBUG.
            logger.warn("[PAGBANK] Falha ao criar pedido PIX (paymentId={})", payment.getId());
            logger.debug("[PAGBANK] Detalhe técnico da falha de criação de pedido PIX", ex);
            throw new PaymentGatewayException("Falha ao comunicar com o gateway de pagamento", ex);
        }

        if (response == null) {
            throw new PaymentGatewayException("Resposta vazia do gateway de pagamento");
        }

        String pspOrderId = (String) response.get("id");
        List<Map<String, Object>> qrCodes = (List<Map<String, Object>>) response.get("qr_codes");
        if (pspOrderId == null || qrCodes == null || qrCodes.isEmpty()) {
            throw new PaymentGatewayException("Resposta inesperada do gateway de pagamento");
        }

        Map<String, Object> firstQrCode = qrCodes.get(0);
        String copiaECola = (String) firstQrCode.get("text");
        String qrCodeImageUrl = extractQrCodeImageUrl(firstQrCode);

        return new PixOrderResult(pspOrderId, copiaECola, qrCodeImageUrl);
    }

    @SuppressWarnings("unchecked")
    private String extractQrCodeImageUrl(Map<String, Object> qrCode) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) qrCode.get("links");
        if (links == null) {
            return null;
        }
        return links.stream()
                .filter(link -> "QRCODE.PNG".equals(link.get("rel")))
                .map(link -> (String) link.get("href"))
                .findFirst()
                .orElse(null);
    }

    /**
     * Consulta o status atual do pedido no PagBank, para reconciliação (ex: chamado pelo
     * endpoint de webhook antes de confirmar pagamento — nunca confia no corpo da notificação
     * recebida, sempre revalida com uma chamada autenticada nossa).
     *
     * @return status do primeiro charge do pedido, ou {@code null} se ainda não houver
     *         nenhum charge associado (pedido criado mas ainda não pago)
     */
    @SuppressWarnings("unchecked")
    public String fetchOrderStatus(String pspOrderId) {
        requireConfiguredToken();

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri("/orders/{id}", pspOrderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            logger.warn("[PAGBANK] Falha ao consultar status do pedido PIX (pspOrderId={})", pspOrderId);
            logger.debug("[PAGBANK] Detalhe técnico da falha de consulta de status", ex);
            throw new PaymentGatewayException("Falha ao comunicar com o gateway de pagamento", ex);
        }

        if (response == null) {
            return null;
        }

        List<Map<String, Object>> charges = (List<Map<String, Object>>) response.get("charges");
        if (charges == null || charges.isEmpty()) {
            return null;
        }

        return (String) charges.get(0).get("status");
    }

    private void requireConfiguredToken() {
        if (apiToken == null || apiToken.isBlank()) {
            throw new PaymentGatewayException("Gateway de pagamento não configurado");
        }
    }
}
