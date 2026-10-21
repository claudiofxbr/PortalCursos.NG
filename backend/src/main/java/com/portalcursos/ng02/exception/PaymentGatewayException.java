package com.portalcursos.ng02.exception;

/**
 * Falha de comunicação com o gateway de pagamento externo (PSP) — indisponibilidade,
 * timeout, erro HTTP 4xx/5xx retornado pelo PSP, ou ausência de configuração de
 * credenciais. Não é uma violação de regra de negócio do domínio (por isso não
 * estende {@link BusinessException}); mapeada para HTTP 502 em
 * {@link GlobalExceptionHandler}.
 */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
