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
        logger.info("[V30.0-SUPREME] Iniciando Monitor de Resiliência de Dados...");
        new Thread(this::checkDatabaseConnection, "Neon-Supreme-Monitor").start();
    }

    private void checkDatabaseConnection() {
        int attempt = 1;
        while (attempt <= MAX_ATTEMPTS && !databaseReady) {
            String maskedUrl = dbUrl.replaceAll(":[^/@]+@", ":****@");
            logger.info("[V30.0-SUPREME] Tentativa {}/{} - Verificando conexão: {}", attempt, MAX_ATTEMPTS, maskedUrl);
            
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
                if (connection.isValid(5)) {
                    // Deep Health Check: Verificar se pelo menos a tabela 'users' existe
                    try (var statement = connection.createStatement()) {
                        statement.executeQuery("SELECT 1 FROM users LIMIT 1");
                        logger.info("[V30.0-SUPREME] SUCESSO: Banco Neon e Esquema validados na tentativa {}!", attempt);
                        databaseReady = true;
                    } catch (SQLException schemaEx) {
                        logger.warn("[V30.0-SUPREME] Conectado, mas esquema (tabelas) ainda não detectado. Aguardando inicialização SQL...");
                    }
                }
            } catch (SQLException e) {
                logger.warn("[V30.0-SUPREME] Infraestrutura Cloud em aquecimento (Cold Start). Erro: {}", e.getMessage());
            }

            if (!databaseReady) {
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
            logger.error("[V30.0-SUPREME] CRÍTICO: Banco de dados ou esquema inacessível após {}s.", (MAX_ATTEMPTS * SLEEP_TIME / 1000));
        }
    }
}
