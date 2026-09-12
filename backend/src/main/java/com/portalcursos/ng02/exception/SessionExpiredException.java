package com.portalcursos.ng02.exception;

/**
 * Refresh token encontrado mas expirado ({@code AuthController.refreshtoken}).
 * Fora do {@link GlobalExceptionHandler} de propósito: o corpo de resposta aqui é o
 * formato legado {@code {"message": "..."}}, sem os campos timestamp/status/error/path
 * que o handler central adiciona — mudar o formato quebraria clientes existentes.
 */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException(String message) {
        super(message);
    }
}
