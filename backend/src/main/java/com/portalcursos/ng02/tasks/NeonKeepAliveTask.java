package com.portalcursos.ng02.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    // Executa a cada 5 minutos (300.000 ms)
    @Scheduled(fixedRate = 300000)
    public void keepNeonAwake() {
        try {
            logger.info("[V21.1-ULTRA] Emitindo Heartbeat para Banco Neon...");
            jdbcTemplate.execute("SELECT 1");
            logger.info("[V21.1-ULTRA] Banco Neon Ativo e Responsivo.");
        } catch (Exception e) {
            logger.error("[V21.1-ULTRA] Falha ao emitir Heartbeat: {}", e.getMessage());
        }
    }
}
