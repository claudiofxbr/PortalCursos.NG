package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.CardCheckoutResult;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Nenhuma chamada de rede real: o HTTP é interceptado por {@link MockRestServiceServer}. */
class PagBankCheckoutGatewayServiceTest {

    private static final String BASE_URL = "https://fake-pagbank.example.test";
    private static final String HOOK = "https://api.example.test/api/finance/card/webhook/42/tok";
    private static final String BACK = "https://app.example.test/finance";

    private MockRestServiceServer mockServer;
    private PagBankCheckoutGatewayService service;

    private void setUpWithToken(String token) {
        service = new PagBankCheckoutGatewayService(token, BASE_URL);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(service, "restClient", builder.build());
    }

    private Payment samplePayment(String amount) {
        return Payment.builder()
                .id(42L)
                .amount(new BigDecimal(amount))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.of(2026, 12, 1))
                .description("Mensalidade Dezembro")
                .build();
    }

    private static String ok(String id, String href) {
        return "{\"id\":\"" + id + "\",\"status\":\"ACTIVE\",\"links\":["
                + "{\"rel\":\"SELF\",\"href\":\"https://x.example/self\"},"
                + "{\"rel\":\"PAY\",\"href\":\"" + href + "\"}]}";
    }

    @Test
    void criaCheckoutComCentavosObjetosDeMeioEUrlsDeNotificacao() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/checkouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer valid-token"))
                .andExpect(jsonPath("$.reference_id").value("PORTAL-CARD-42"))
                .andExpect(jsonPath("$.items[0].unit_amount").value(19990))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.payment_methods[0].type").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.payment_methods[1].type").value("DEBIT_CARD"))
                .andExpect(jsonPath("$.payment_methods.length()").value(2))
                .andExpect(jsonPath("$.redirect_url").value(BACK))
                .andExpect(jsonPath("$.payment_notification_urls[0]").value(HOOK))
                .andExpect(jsonPath("$.notification_urls[0]").value(HOOK))
                .andExpect(jsonPath("$.customer").doesNotExist())
                .andRespond(withSuccess(ok("CHEC_ABC-123", "https://pagamento.sandbox.pagbank.com.br/pagamento?code=x"),
                        MediaType.APPLICATION_JSON));

        CardCheckoutResult r = service.createCardCheckout(samplePayment("199.90"), BACK, HOOK, true);

        assertEquals("CHEC_ABC-123", r.checkoutId());
        assertEquals("https://pagamento.sandbox.pagbank.com.br/pagamento?code=x", r.payUrl());
        assertEquals(19990L, r.chargedAmountCents());
        mockServer.verify();
    }

    @Test
    void semDebitoAprovadoEnviaSomenteCredito() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/checkouts"))
                .andExpect(jsonPath("$.payment_methods.length()").value(1))
                .andExpect(jsonPath("$.payment_methods[0].type").value("CREDIT_CARD"))
                .andRespond(withSuccess(ok("CHEC_ABC-123", "https://pay.example/p"), MediaType.APPLICATION_JSON));

        service.createCardCheckout(samplePayment("10.00"), BACK, HOOK, false);
        mockServer.verify();
    }

    @Test
    void faturaVencidaCobraOTotalComJurosEmCentavos() {
        setUpWithToken("valid-token");
        Payment overdue = samplePayment("100.00");
        overdue.setStatus(EPaymentStatus.OVERDUE);
        overdue.setDueDate(LocalDate.now().minusDays(10)); // juros: 100 * 0,0006 * 10 = 0,60
        mockServer.expect(requestTo(BASE_URL + "/checkouts"))
                .andExpect(jsonPath("$.items[0].unit_amount").value(10060))
                .andRespond(withSuccess(ok("CHEC_ABC-123", "https://pay.example/p"), MediaType.APPLICATION_JSON));

        assertEquals(10060L, service.createCardCheckout(overdue, BACK, HOOK, true).chargedAmountCents());
    }

    @Test
    void erro4xxNaoVazaCorpoDoPsp() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/checkouts"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error_messages\":[{\"description\":\"segredo do psp\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class,
                () -> service.createCardCheckout(samplePayment("10.00"), BACK, HOOK, true));
        assertFalse(ex.getMessage().contains("segredo do psp"));
    }

    @Test
    void erro5xxViraPaymentGatewayException() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/checkouts")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThrows(PaymentGatewayException.class,
                () -> service.createCardCheckout(samplePayment("10.00"), BACK, HOOK, true));
    }

    @Test
    void respostaSemLinkPayOuComLinkInseguroOuIdInvalidoEhRecusada() {
        String[] bad = {
                "{\"id\":\"CHEC_ABC-123\",\"links\":[{\"rel\":\"SELF\",\"href\":\"https://x.example/s\"}]}",
                ok("CHEC_ABC-123", "http://pay.example/p"),
                ok("CHEC_ABC-123", "https://user:pass@pay.example/p"),
                ok("CHEC_ABC-123", "javascript:alert(1)"),
                ok("a b", "https://pay.example/p"),
                "{\"links\":[{\"rel\":\"PAY\",\"href\":\"https://pay.example/p\"}]}",
        };
        for (String body : bad) {
            setUpWithToken("valid-token");
            mockServer.expect(requestTo(BASE_URL + "/checkouts"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThrows(PaymentGatewayException.class,
                    () -> service.createCardCheckout(samplePayment("10.00"), BACK, HOOK, true), body);
        }
    }

    @Test
    void tokenVazioOuValorZeroNaoChamamOPsp() {
        setUpWithToken("");
        assertThrows(PaymentGatewayException.class,
                () -> service.createCardCheckout(samplePayment("10.00"), BACK, HOOK, true));
        mockServer.verify();

        setUpWithToken("valid-token");
        assertThrows(PaymentGatewayException.class,
                () -> service.createCardCheckout(samplePayment("0.00"), BACK, HOOK, true));
        mockServer.verify();
    }
}
