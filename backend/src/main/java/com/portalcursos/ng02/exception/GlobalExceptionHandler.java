package com.portalcursos.ng02.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import jakarta.validation.ConstraintViolationException;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        logger.warn("[AUTH WARN] Bad credentials attempt: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", new Date());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", "Usuário ou senha inválidos.");
        body.put("path", request.getDescription(false));

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<?> handleLockedException(LockedException ex, WebRequest request) {
        logger.warn("[AUTH WARN] Locked account attempt: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", new Date());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Locked");
        body.put("message", "Sua conta ou IP foi temporariamente bloqueado devido a múltiplas tentativas falhas. Tente novamente em 24 horas.");
        body.put("path", request.getDescription(false));

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", new Date());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", "Unauthorized");
        body.put("message", "Falha na autenticação: " + ex.getMessage());
        body.put("path", request.getDescription(false));

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        logger.error("[403 FORBIDDEN] Acesso negado: {}", ex.getMessage());
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("error", "Forbidden");
        body.put("message", "Acesso Negado: Você não tem permissão para esta ação.");
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityException(org.springframework.dao.DataIntegrityViolationException ex, WebRequest request) {
        logger.warn("[SUPREME-WARN] Violação de integridade nos dados: {}", ex.getMostSpecificCause().getMessage());
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict / Integrity Violation");
        body.put("message", "Violação de integridade: Verifique se o registro já existe ou se há campos obrigatórios vazios.");
        body.put("path", request.getDescription(false).replace("uri=", ""));
        body.put("hint", "Verifique duplicidade de CPF/E-mail ou campos não preenchidos.");

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParams(MissingServletRequestParameterException ex, WebRequest request) {
        logger.warn("[FRONTEND-BAD-REQUEST] Parâmetro obrigatório ausente: {}", ex.getParameterName());
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request / Missing Parameter");
        body.put("message", "O campo '" + ex.getParameterName() + "' é obrigatório para concluir o cadastro.");
        body.put("path", request.getDescription(false).replace("uri=", ""));
        body.put("hint", "Verifique se todos os campos marcados com * foram preenchidos corretamente.");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        logger.warn("[VALIDATION-ERROR] Falha de validação de dados: {}", ex.getMessage());
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Error");
        body.put("message", "Dados inválidos: " + ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        logger.warn("[404 NOT FOUND] Recurso não encontrado: {}", ex.getResourcePath());
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "O endpoint solicitado não existe: " + ex.getResourcePath());
        body.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
    public ResponseEntity<?> handleTransactionException(org.springframework.transaction.TransactionSystemException ex, WebRequest request) {
        logger.error("[SUPREME-TRANSACTION-ERROR] Falha de transação/rollback: {}", ex.getMessage());
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Transaction Failure");
        body.put("message", "Instabilidade temporária na persistência de dados. Tente novamente em instantes.");
        body.put("path", request.getDescription(false).replace("uri=", ""));
        body.put("hint", "O sistema de auto-cura está estabilizando a conexão com o banco Cloud.");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<?> handleDatabaseException(org.springframework.dao.DataAccessException ex, WebRequest request) {
        logger.error("[SUPREME-ERROR] Falha de persistência no banco: ", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", new Date());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Service Unavailable");
        body.put("message", "O banco de dados (Neon Cloud) está indisponível ou em Cold Start. O Protocolo V30.9-SUPREME está tentando restabelecer a conexão.");
        body.put("path", request.getDescription(false));
        body.put("hint", "Aguarde alguns segundos para o aquecimento automático da infraestrutura.");

        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String error, String message, WebRequest request, String hint) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));
        if (hint != null) body.put("hint", hint);
        
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }

        return new ResponseEntity<>(body, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        String correlationId = MDC.get("correlationId");
        logger.error("[SUPREME-ERROR-AUDIT][ID:{}] Exceção não tratada: {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR, 
            "Internal Server Error", 
            "Ocorreu um erro interno. Protocolo de rastreamento: " + correlationId, 
            request, 
            "Tente novamente em alguns segundos."
        );
    }
}
