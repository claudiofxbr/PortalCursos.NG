package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.CardPaymentFacts;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Reconsulta do checkout/pedidos no PagBank (HTTP sempre mockado). */
class PagBankCheckoutFactsTest {

    private static final String BASE_URL = "https://fake-pagbank.example.test";
    private static final String CHEC = "CHEC_0123-ABCD";
    private static final String REF = "PORTAL-CARD-42";

    private MockRestServiceServer mockServer;
    private PagBankCheckoutGatewayService service;

    private void setUp(String token) {
        service = new PagBankCheckoutGatewayService(token, BASE_URL);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(service, "restClient", builder.build());
    }

    private static String checkout(String ref, String... orderIds) {
        StringBuilder orders = new StringBuilder();
        for (String id : orderIds) {
            if (orders.length() > 0) orders.append(',');
            orders.append("{\"id\":\"").append(id).append("\"}");
        }
        return "{\"id\":\"" + CHEC + "\",\"status\":\"ACTIVE\",\"reference_id\":\"" + ref + "\",\"orders\":[" + orders + "]}";
    }

    private static String order(String ref, String status, int cents, String type) {
        return "{\"id\":\"ORDE_X\",\"reference_id\":\"" + ref + "\",\"charges\":[{\"status\":\"" + status
                + "\",\"amount\":{\"value\":" + cents + ",\"currency\":\"BRL\"},\"payment_method\":{\"type\":\"" + type + "\"}}]}";
    }

    @Test
    void checkoutPagoSomaCobrancasPagasEInformaMeio() {
        setUp("tok");
        mockServer.expect(requestTo(BASE_URL + "/checkouts/" + CHEC))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess(checkout(REF, "ORDE_1111-AAAA"), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/orders/ORDE_1111-AAAA"))
                .andRespond(withSuccess(order(REF, "PAID", 19990, "DEBIT_CARD"), MediaType.APPLICATION_JSON));

        CardPaymentFacts f = service.resolveCheckoutFacts(CHEC);

        assertEquals(REF, f.referenceId());
        assertTrue(f.paid());
        assertEquals(19990L, f.paidAmountCents());
        assertEquals("DEBIT_CARD", f.methodType());
        mockServer.verify();
    }

    @Test
    void checkoutSemPedidosAindaNaoFoiPagoRetornaNull() {
        setUp("tok");
        mockServer.expect(requestTo(BASE_URL + "/checkouts/" + CHEC))
                .andRespond(withSuccess(checkout(REF), MediaType.APPLICATION_JSON));

        assertNull(service.resolveCheckoutFacts(CHEC));
    }

    @Test
    void cobrancaNaoPagaNaoMarcaPago() {
        setUp("tok");
        mockServer.expect(requestTo(BASE_URL + "/checkouts/" + CHEC))
                .andRespond(withSuccess(checkout(REF, "ORDE_1111-AAAA"), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/orders/ORDE_1111-AAAA"))
                .andRespond(withSuccess(order(REF, "DECLINED", 19990, "CREDIT_CARD"), MediaType.APPLICATION_JSON));

        CardPaymentFacts f = service.resolveCheckoutFacts(CHEC);

        assertFalse(f.paid());
        assertNull(f.paidAmountCents());
    }

    @Test
    void pedidoDeOutraReferenciaEIgnorado() {
        setUp("tok");
        mockServer.expect(requestTo(BASE_URL + "/checkouts/" + CHEC))
                .andRespond(withSuccess(checkout(REF, "ORDE_1111-AAAA"), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/orders/ORDE_1111-AAAA"))
                .andRespond(withSuccess(order("OUTRA-REF", "PAID", 19990, "CREDIT_CARD"), MediaType.APPLICATION_JSON));

        assertNull(service.resolveCheckoutFacts(CHEC), "pedido de outra referência não pode contar como pago");
    }

    @Test
    void valorIlegivelDeixaValorPagoNulo() {
        setUp("tok");
        mockServer.expect(requestTo(BASE_URL + "/checkouts/" + CHEC))
                .andRespond(withSuccess(checkout(REF, "ORDE_1111-AAAA"), MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(BASE_URL + "/orders/ORDE_1111-AAAA"))
                .andRespond(withSuccess("{\"reference_id\":\"" + REF + "\",\"charges\":[{\"status\":\"PAID\",\"amount\":{\"value\":\"abc\"}}]}",
                        MediaType.APPLICATION_JSON));

        CardPaymentFacts f = service.resolveCheckoutFacts(CHEC);

        assertTrue(f.paid());
        assertNull(f.paidAmountCents());
    }

    @Test
    void idsMalformadosNaoSaoConsultados() {
        setUp("tok");
        mockServer.expect(requestTo(BASE_URL + "/checkouts/" + CHEC))
                .andRespond(withSuccess(checkout(REF, "../etc/passwd", "ORDE_"), MediaType.APPLICATION_JSON));

        assertNull(service.resolveCheckoutFacts(CHEC));
        mockServer.verify();
    }

    @Test
    void checkoutIdInvalidoOuNuloNaoChamaOPsp() {
        setUp("tok");
        assertNull(service.resolveCheckoutFacts("../x"));
        assertNull(service.resolveCheckoutFacts(null));
        mockServer.verify();
    }

    @Test
    void falhaDoPspVirarPaymentGatewayExceptionSemVazarCorpo() {
        setUp("tok");
        mockServer.expect(requestTo(BASE_URL + "/checkouts/" + CHEC))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("segredo do psp"));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class, () -> service.resolveCheckoutFacts(CHEC));
        assertFalse(ex.getMessage().contains("segredo do psp"));
    }

    @Test
    void tokenVazioLancaExcecao() {
        setUp("");
        assertThrows(PaymentGatewayException.class, () -> service.resolveCheckoutFacts(CHEC));
    }
}
