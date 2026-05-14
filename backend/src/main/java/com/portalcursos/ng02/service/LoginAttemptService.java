package com.portalcursos.ng02.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serviço de Proteção contra Força Bruta (Brute Force Protection)
 * Monitora tentativas de login por IP e bloqueia temporariamente após excesso de falhas.
 * Protocolo OMEGA-SECURITY.
 */
@Service
public class LoginAttemptService {
    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);
    
    private final int MAX_ATTEMPT = 5;
    private final int BLOCK_DURATION_MINUTES = 15;
    
    private Map<String, Integer> attemptsCache = new ConcurrentHashMap<>();
    private Map<String, LocalDateTime> blockCache = new ConcurrentHashMap<>();

    public void loginSucceeded(String ip) {
        attemptsCache.remove(ip);
        blockCache.remove(ip);
    }

    public void loginFailed(String ip) {
        int attempts = attemptsCache.getOrDefault(ip, 0);
        attempts++;
        attemptsCache.put(ip, attempts);
        
        logger.warn("[SECURITY-AUDIT] Falha de login detectada para o IP: {}. Tentativa: {}/{}", ip, attempts, MAX_ATTEMPT);
        
        if (attempts >= MAX_ATTEMPT) {
            LocalDateTime unblockTime = LocalDateTime.now().plusMinutes(BLOCK_DURATION_MINUTES);
            blockCache.put(ip, unblockTime);
            logger.error("[SECURITY-ALERT] IP BLOQUEADO por força bruta: {}. Desbloqueio em: {}", ip, unblockTime);
        }
    }

    public boolean isBlocked(String ip) {
        if (blockCache.containsKey(ip)) {
            if (blockCache.get(ip).isBefore(LocalDateTime.now())) {
                blockCache.remove(ip);
                attemptsCache.remove(ip);
                logger.info("[SECURITY-INFO] IP desbloqueado automaticamente (tempo expirado): {}", ip);
                return false;
            }
            return true;
        }
        return false;
    }
    
    public int getRemainingAttempts(String ip) {
        return MAX_ATTEMPT - attemptsCache.getOrDefault(ip, 0);
    }
}
