package com.portalcursos.ng02.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * Cobertura do handler novo de PaymentGatewayException (integração real do PIX com o
 * PagBank) — segue o mesmo padrão dos demais handlers do projeto: corpo com
 * timestamp/status/error/message/path, mensagem genérica (nunca o detalhe técnico do
 * PSP, que fica só no log).
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private WebRequest webRequest;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void paymentGatewayExceptionMapeiaPara502ComMensagemGenerica() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/finance/generate-pix/42");
        PaymentGatewayException ex = new PaymentGatewayException(
                "detalhe técnico sensível do PSP que não deve vazar ao cliente");

        ResponseEntity<?> response = handler.handlePaymentGatewayException(ex, webRequest);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(502, body.get("status"));
        assertEquals("Bad Gateway", body.get("error"));
        assertFalse(body.get("message").toString().contains("detalhe técnico sensível"));
        assertEquals("/api/finance/generate-pix/42", body.get("path"));
    }
}
