package com.portalcursos.ng02.tasks;

import com.portalcursos.ng02.repository.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Protocolo V21.1-ULTRA: Neon Keep-Alive Heartbeat
 * Objetivo: Impedir que o tier serverless do Neon hiberne por inatividade,
 * garantindo respostas instantâneas para os usuários.
 */
@Component
public class NeonKeepAliveTask {

    private static final Logger logger = LoggerFactory.getLogger(NeonKeepAliveTask.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    /** Heartbeat a cada 5 minutos para manter o Neon serverless ativo. */
    @Scheduled(fixedRate = 300000)
    public void keepNeonAwake() {
        try {
            jdbcTemplate.execute("SELECT 1");
            logger.info("[KEEPALIVE] Banco Neon ativo.");
        } catch (Exception e) {
            logger.error("[KEEPALIVE] Falha no heartbeat: {}", e.getMessage());
        }
    }

    /** Limpeza diária de registros de tentativas de login expirados há mais de 24h. */
    @Scheduled(fixedRate = 86400000)
    public void cleanExpiredLoginAttempts() {
        try {
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            loginAttemptRepository.deleteExpiredBefore(cutoff);
            logger.info("[KEEPALIVE] Registros de login_attempts expirados removidos.");
        } catch (Exception e) {
            logger.error("[KEEPALIVE] Falha na limpeza de login_attempts: {}", e.getMessage());
        }
    }
}
