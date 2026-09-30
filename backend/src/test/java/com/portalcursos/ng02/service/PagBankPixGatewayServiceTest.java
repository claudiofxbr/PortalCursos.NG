package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.PixOrderResult;
import com.portalcursos.ng02.exception.PaymentGatewayException;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Cobertura do cliente HTTP real do PagBank/PagSeguro. Nenhum teste aqui faz chamada de
 * rede de verdade (nem sandbox real, nem localhost real) — a chamada HTTP é interceptada
 * via {@link MockRestServiceServer}, injetada no bean por reflexão (o {@code RestClient}
 * é construído internamente no construtor do serviço, sem ponto de injeção público).
 */
class PagBankPixGatewayServiceTest {

    private static final String BASE_URL = "https://fake-pagbank.example.test";

    private MockRestServiceServer mockServer;
    private PagBankPixGatewayService service;

    private void setUpWithToken(String token) {
        service = new PagBankPixGatewayService(token, BASE_URL);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(service, "restClient", builder.build());
    }

    private Payment samplePayment() {
        Student student = Student.builder()
                .id(1L)
                .fullName("Fulano de Tal")
                .email("fulano@example.com")
                .cpf("12345678900")
                .build();
        return Payment.builder()
                .id(42L)
                .amount(new BigDecimal("199.90"))
                .status(EPaymentStatus.PENDING)
                .dueDate(LocalDate.of(2026, 12, 1))
                .description("Mensalidade Dezembro")
                .student(student)
                .build();
    }

    @Test
    void createPixOrderComSucessoMapeiaIdTextELinkDoQrCode() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer valid-token"))
                .andRespond(withSuccess("""
                        {
                          "id": "ORDE_ABC123",
                          "qr_codes": [{
                            "id": "QRCO_1",
                            "text": "00020126580014BR.GOV.BCB.PIX...",
                            "links": [{"rel": "QRCODE.PNG", "href": "https://pagbank.example/qr.png"}]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        PixOrderResult result = service.createPixOrder(samplePayment(), "webhook-token", BASE_URL + "/webhook");

        assertEquals("ORDE_ABC123", result.pspOrderId());
        assertEquals("00020126580014BR.GOV.BCB.PIX...", result.copiaECola());
        assertEquals("https://pagbank.example/qr.png", result.qrCodeImageUrl());
        mockServer.verify();
    }

    @Test
    void createPixOrderQuando4xxLancaPaymentGatewayExceptionSemVazarCorpoDoPsp() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error_messages\":[{\"description\":\"invalid tax_id\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class,
                () -> service.createPixOrder(samplePayment(), "webhook-token", BASE_URL + "/webhook"));

        assertFalse(ex.getMessage().contains("invalid tax_id"),
                "mensagem exposta não deve conter o corpo de erro bruto do PSP");
        mockServer.verify();
    }

    @Test
    void createPixOrderQuando5xxLancaPaymentGatewayException() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("upstream failure")
                        .contentType(MediaType.TEXT_PLAIN));

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class,
                () -> service.createPixOrder(samplePayment(), "webhook-token", BASE_URL + "/webhook"));

        assertFalse(ex.getMessage().contains("upstream failure"));
        mockServer.verify();
    }

    @Test
    void createPixOrderQuandoConexaoFalhaLancaPaymentGatewayExceptionSemVazarExcecaoCrua() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(request -> {
                    throw new IOException("Connection timed out");
                });

        PaymentGatewayException ex = assertThrows(PaymentGatewayException.class,
                () -> service.createPixOrder(samplePayment(), "webhook-token", BASE_URL + "/webhook"));

        assertFalse(ex.getMessage().contains("Connection timed out"));
        mockServer.verify();
    }

    @Test
    void createPixOrderSemTokenConfiguradoFalhaSemChamarHttp() {
        setUpWithToken("");

        assertThrows(PaymentGatewayException.class,
                () -> service.createPixOrder(samplePayment(), "webhook-token", BASE_URL + "/webhook"));
        // Nenhuma expectativa de requisição foi registrada: se o código tivesse tentado
        // chamar o PagBank mesmo assim, o MockRestServiceServer teria lançado AssertionError
        // (não capturado por PaymentGatewayException), e o assertThrows acima teria falhado.
    }

    @Test
    void createPixOrderComTokenEmBrancoFalhaSemChamarHttp() {
        setUpWithToken("   ");

        assertThrows(PaymentGatewayException.class,
                () -> service.createPixOrder(samplePayment(), "webhook-token", BASE_URL + "/webhook"));
    }

    @Test
    void fetchOrderStatusRetornaStatusDoPrimeiroCharge() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/orders/ORDE_ABC123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"charges\":[{\"status\":\"PAID\"}]}", MediaType.APPLICATION_JSON));

        String status = service.fetchOrderStatus("ORDE_ABC123");

        assertEquals("PAID", status);
        mockServer.verify();
    }

    @Test
    void fetchOrderStatusSemTokenConfiguradoFalhaSemChamarHttp() {
        setUpWithToken(null);

        assertThrows(PaymentGatewayException.class, () -> service.fetchOrderStatus("ORDE_ABC123"));
    }

    @Test
    void fetchOrderStatusQuandoPagBankFalhaLancaPaymentGatewayException() {
        setUpWithToken("valid-token");
        mockServer.expect(requestTo(BASE_URL + "/orders/ORDE_ABC123"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(PaymentGatewayException.class, () -> service.fetchOrderStatus("ORDE_ABC123"));
        mockServer.verify();
    }
}
