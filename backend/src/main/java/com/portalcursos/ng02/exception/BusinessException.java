package com.portalcursos.ng02.exception;

/** Violação de regra de negócio (dados incompletos, regra de domínio não atendida). Mapeada para HTTP 400. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
