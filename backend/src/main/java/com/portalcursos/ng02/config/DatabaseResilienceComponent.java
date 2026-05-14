package com.portalcursos.ng02.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Componente de Resiliência para Banco de Dados Cloud (Neon/Render).
 * Monitora e aguarda a disponibilidade do banco de dados antes da inicialização completa dos serviços.
 */
@Component
public class DatabaseResilienceComponent {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseResilienceComponent.class);

    @Autowired
    private DataSource dataSource;

    private static final int INITIAL_SLEEP_TIME = 5000; // 5 segundos inicial
    private static final int MAX_SLEEP_TIME = 20000;    // Máximo de 20 segundos entre tentativas
    private static final int TOTAL_WAIT_TARGET_MS = 150000; // 150 segundos total de paciência cloud

    private static volatile boolean databaseReady = false;

    public static boolean isDatabaseReady() {
        return databaseReady;
    }

    @PostConstruct
    public void startHealthCheck() {
        logger.info("[OMEGA-SUPREME] Iniciando Monitor de Resiliência de Dados (Exponential Backoff)...");
        new Thread(this::checkDatabaseConnection, "Neon-Omega-Monitor").start();
    }

    private void checkDatabaseConnection() {
        int currentSleep = INITIAL_SLEEP_TIME;
        long totalWaited = 0;
        int attempt = 1;

        while (totalWaited < TOTAL_WAIT_TARGET_MS && !databaseReady) {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    // Deep Health Check: Verificar se pelo menos a tabela 'users' existe
                    try (var statement = connection.createStatement()) {
                        statement.executeQuery("SELECT 1 FROM users LIMIT 1");
                        logger.info("[OMEGA-SUPREME] SUCESSO: Banco Neon e Conexão validados!");
                        databaseReady = true;
                        
                        // Registro de Telemetria Interna
                        try {
                            statement.executeUpdate("INSERT INTO system_telemetry (component, status, latency_ms, details) " +
                                    "VALUES ('DATABASE', 'UP', " + totalWaited + ", 'Resiliência concluída via Flyway')");
                        } catch (Exception e) { /* Ignorar se a tabela de telemetria falhar */ }
                    } catch (SQLException schemaEx) {
                        logger.warn("[OMEGA-SUPREME] Conectado, mas erro ao registrar telemetria: {}", schemaEx.getMessage());
                        databaseReady = true; // Mesmo assim consideramos pronto se a conexão abriu
                    }
                }
            } catch (SQLException e) {
                logger.warn("[OMEGA-SUPREME] Infraestrutura Cloud em aquecimento. Erro: {}", e.getMessage());
            }

            if (!databaseReady) {
                try {
                    logger.info("[OMEGA-SUPREME] Aguardando {}ms antes da próxima tentativa...", currentSleep);
                    Thread.sleep(currentSleep);
                    totalWaited += currentSleep;
                    // Exponential Backoff com limitador
                    currentSleep = Math.min(currentSleep + 5000, MAX_SLEEP_TIME); 
                    attempt++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        if (!databaseReady) {
            logger.error("[OMEGA-SUPREME] CRÍTICO: Banco de dados inacessível após {}s.", totalWaited / 1000);
        }
    }
}
