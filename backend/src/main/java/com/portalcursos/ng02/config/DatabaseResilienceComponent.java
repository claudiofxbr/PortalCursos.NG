package com.portalcursos.ng02.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Componente de Resiliência para Banco de Dados Cloud (Neon/Render).
 * Monitora e aguarda a disponibilidade do banco de dados antes da inicialização completa dos serviços.
 */
@Component
public class DatabaseResilienceComponent {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseResilienceComponent.class);

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private static final int MAX_ATTEMPTS = 12; // 12 tentivas * 10s = 120s (tempo de cold start do Neon)
    private static final int SLEEP_TIME = 10000; // 10 segundos

    private static volatile boolean databaseReady = false;

    public static boolean isDatabaseReady() {
        return databaseReady;
    }

    @PostConstruct
    public void startHealthCheck() {
        logger.info("[V22-ULTRA] Iniciando Monitor de Resiliência REATIVO (Non-Blocking)...");
        new Thread(this::checkDatabaseConnection, "Neon-Health-Monitor").start();
    }

    private void checkDatabaseConnection() {
        int attempt = 1;
        while (attempt <= MAX_ATTEMPTS && !databaseReady) {
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
                if (connection.isValid(5)) {
                    logger.info("[V22-ULTRA] SUCESSO: Conexão com o banco estabelecida na tentativa {}!", attempt);
                    databaseReady = true;
                }
            } catch (SQLException e) {
                logger.warn("[V22-ULTRA] Aguardando Banco Neon (Tentativa {}/{})...", attempt, MAX_ATTEMPTS);
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                attempt++;
            }
        }

        if (!databaseReady) {
            logger.error("[V22-ULTRA] CRÍTICO: Banco de dados inacessível após {}s.", (MAX_ATTEMPTS * SLEEP_TIME / 1000));
        }
    }
}
