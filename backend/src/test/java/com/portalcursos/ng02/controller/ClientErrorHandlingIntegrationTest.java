package com.portalcursos.ng02.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.PagBankCheckoutGatewayService;
import com.portalcursos.ng02.service.PagBankPixGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regressão de E-01 (relatório de testes de 20/10/2026): erro de entrada do cliente em rotas públicas
 * (os webhooks de pagamento) devolvia HTTP 500 e gerava ERROR no log. Agora: 400 / 405 / 415, sem
 * stack trace e sem consultar o banco nem o PagBank.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:clienterrortestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
@AutoConfigureMockMvc
class ClientErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PagBankPixGatewayService pixGateway;

    @MockitoBean
    private PagBankCheckoutGatewayService checkoutGateway;

    @Test
    void idNaoNumericoNoWebhookDoPixRetorna400() throws Exception {
        mockMvc.perform(post("/api/finance/pix/webhook/abc/x").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("O parâmetro 'paymentId' tem um valor inválido."));
        verifyNoInteractions(paymentRepository, pixGateway);
    }

    @Test
    void idForaDoIntervaloDeLongNoWebhookDeCartaoRetorna400() throws Exception {
        mockMvc.perform(post("/api/finance/card/webhook/99999999999999999999999/x")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(paymentRepository, checkoutGateway);
    }

    @Test
    void getNoWebhookRetorna405ComAllowPost() throws Exception {
        mockMvc.perform(get("/api/finance/pix/webhook/1/x"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(get("/api/finance/card/webhook/1/x"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void tipoDeConteudoNaoSuportadoNoWebhookDoPixRetorna415() throws Exception {
        mockMvc.perform(post("/api/finance/pix/webhook/1/x").contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void respostasDeErroNaoVazamStackTraceNemValorRecebido() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/finance/pix/webhook/valor-secreto-recebido/x")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // O campo "path" repete a URL enviada pelo próprio cliente (convenção de todos os erros do sistema);
        // a mensagem de erro, não.
        String message = com.jayway.jsonpath.JsonPath.read(body, "$.message");
        assertTrue(!message.contains("valor-secreto-recebido"), "a mensagem não deve repetir o valor recebido");
        assertTrue(!body.contains("java.") && !body.contains("org.springframework") && !body.contains("at com."),
                "não deve haver stack trace na resposta");
    }

    @Test
    void erroDeClienteNaoAlteraORetornoDeWebhookValido() throws Exception {
        // id numérico, token inválido e fatura inexistente continua sendo 404 genérico (comportamento anterior).
        mockMvc.perform(post("/api/finance/pix/webhook/1/token-invalido")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }
}
