package com.portalcursos.ng02.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.TypeMismatchException;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // Regressão de E-01: erro de entrada do cliente não pode virar 500.
    @Test
    void tipoInvalidoDeParametroMapeiaPara400SemDevolverOValorRecebido() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/finance/pix/webhook/abc/x");

        ResponseEntity<?> response = handler.handleTypeMismatch(new TypeMismatchException("abc-valor-recebido", Long.class), webRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertFalse(body.toString().contains("abc-valor-recebido"), "o valor recebido não deve ser ecoado");
        assertEquals("/api/finance/pix/webhook/abc/x", body.get("path"));
    }

    @Test
    void metodoNaoSuportadoMapeiaPara405ComCabecalhoAllow() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/finance/pix/webhook/1/x");

        ResponseEntity<?> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")), webRequest);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertTrue(response.getHeaders().getAllow().contains(HttpMethod.POST));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(405, body.get("status"));
    }

    @Test
    void metodoNaoSuportadoSemListaDeMetodosAindaDevolve405() {
        when(webRequest.getDescription(false)).thenReturn("uri=/x");

        ResponseEntity<?> response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("GET"), webRequest);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertTrue(response.getHeaders().getAllow().isEmpty());
    }

    @Test
    void tipoDeConteudoNaoSuportadoMapeiaPara415() {
        when(webRequest.getDescription(false)).thenReturn("uri=/api/finance/pix/webhook/1/x");

        ResponseEntity<?> response = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), webRequest);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(415, body.get("status"));
    }
}
