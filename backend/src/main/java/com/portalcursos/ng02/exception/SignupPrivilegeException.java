package com.portalcursos.ng02.exception;

/**
 * Signup pedindo role privilegiada (não ALUNO/CANDIDATO) sem que o requisitante já esteja
 * autenticado como ADMIN/ROOT_MASTER ({@code AuthController.registerUser} via
 * {@code AuthService.signup}).
 * Fora do {@link GlobalExceptionHandler} de propósito: preserva o corpo de resposta legado
 * {@code {"message": "..."}}, diferente do que {@link org.springframework.security.access.AccessDeniedException}
 * produziria via {@code handleAccessDeniedException}.
 */
public class SignupPrivilegeException extends RuntimeException {
    public SignupPrivilegeException(String message) {
        super(message);
    }
}
