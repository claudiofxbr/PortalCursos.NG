package com.portalcursos.ng02.exception;

/**
 * Bloqueio de força bruta por IP ({@link com.portalcursos.ng02.service.LoginAttemptService}).
 * Não é registrada no {@link GlobalExceptionHandler} de propósito: o status HTTP 423 (LOCKED)
 * usado aqui é diferente do 403 que o handler já dá para
 * {@link org.springframework.security.authentication.LockedException} do Spring Security
 * (conta com LockedException no UserDetails) — são bloqueios com causas e semânticas
 * diferentes. Quem lança trata localmente (ver {@code AuthController.authenticateUser}).
 */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
