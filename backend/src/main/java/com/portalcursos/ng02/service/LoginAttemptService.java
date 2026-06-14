package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.LoginAttempt;
import com.portalcursos.ng02.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Proteção contra força bruta baseada em banco de dados.
 * Persiste tentativas de login por IP — sobrevive a restarts e funciona em múltiplas instâncias.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES = 15;

    private final LoginAttemptRepository loginAttemptRepository;

    @Transactional
    public void loginSucceeded(String ip) {
        if (ip == null || ip.isBlank()) return;
        loginAttemptRepository.deleteById(ip);
        logger.debug("[SECURITY] Login bem-sucedido — tentativas resetadas para IP: {}", ip);
    }

    @Transactional
    public void loginFailed(String ip) {
        if (ip == null || ip.isBlank()) return;
        LoginAttempt record = loginAttemptRepository.findById(ip)
                .orElse(LoginAttempt.builder()
                        .ipAddress(ip)
                        .attempts(0)
                        .lastAttempt(Instant.now())
                        .build());

        record.setAttempts(record.getAttempts() + 1);
        record.setLastAttempt(Instant.now());

        logger.warn("[SECURITY] Falha de login para IP: {}. Tentativa: {}/{}", ip, record.getAttempts(), MAX_ATTEMPTS);

        if (record.getAttempts() >= MAX_ATTEMPTS) {
            Instant unblockAt = Instant.now().plus(BLOCK_MINUTES, ChronoUnit.MINUTES);
            record.setBlockedUntil(unblockAt);
            logger.error("[SECURITY] IP BLOQUEADO por força bruta: {}. Desbloqueio em: {}", ip, unblockAt);
        }

        loginAttemptRepository.save(record);
    }

    @Transactional
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return loginAttemptRepository.findById(ip)
                .map(record -> {
                    if (record.getBlockedUntil() == null) return false;

                    if (record.getBlockedUntil().isBefore(Instant.now())) {
                        // Bloqueio expirou — limpa o registro
                        loginAttemptRepository.delete(record);
                        logger.info("[SECURITY] IP desbloqueado automaticamente: {}", ip);
                        return false;
                    }

                    return true;
                })
                .orElse(false);
    }
}
