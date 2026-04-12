package com.portalcursos.ng02.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("[SUPREME-ERROR] Exceção não tratada capturada: ", ex);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", new Date());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "Erro crítico operacional. Protocolo V30.9-SUPREME-FINAL registrou este evento para auditoria.");
        body.put("path", request.getDescription(false));

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
