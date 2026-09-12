package com.portalcursos.ng02.exception;

/**
 * Username ou e-mail já em uso no signup ({@code AuthController.registerUser} via
 * {@code AuthService.signup}).
 * Fora do {@link GlobalExceptionHandler} de propósito: preserva o corpo de resposta legado
 * {@code {"message": "..."}}, sem os campos timestamp/status/error/path que o handler
 * central adiciona.
 */
public class SignupConflictException extends RuntimeException {
    public SignupConflictException(String message) {
        super(message);
    }
}
