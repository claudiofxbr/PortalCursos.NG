package com.portalcursos.ng02.exception;

/**
 * Refresh token não encontrado/inválido ({@code AuthController.refreshtoken}).
 * Fora do {@link GlobalExceptionHandler} pelo mesmo motivo de {@link SessionExpiredException}:
 * preserva o corpo de resposta legado {@code {"message": "..."}}.
 */
public class InvalidSessionException extends RuntimeException {
    public InvalidSessionException(String message) {
        super(message);
    }
}
