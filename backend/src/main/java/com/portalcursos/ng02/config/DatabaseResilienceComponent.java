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
            String maskedUrl = dbUrl.replaceAll(":[^/@]+@", ":****@");
            logger.info("[OMEGA-SUPREME] Tentativa {} - Verificando conexão: {}", attempt, maskedUrl);
            
            try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
                if (connection.isValid(5)) {
                    // Deep Health Check: Verificar se pelo menos a tabela 'users' existe
                    try (var statement = connection.createStatement()) {
                        statement.executeQuery("SELECT 1 FROM users LIMIT 1");
                        logger.info("[OMEGA-SUPREME] SUCESSO: Banco Neon e Esquema OMEGA validados!");
                        databaseReady = true;
                        
                        // AUTO-CORREÇÃO DE SCHEMA (SUPREME V32.0 SELF-HEALING)
                        applySchemaFixes(connection);

                        // Registro de Telemetria Interna
                        try {
                            statement.executeUpdate("INSERT INTO system_telemetry (component, status, latency_ms, details) " +
                                    "VALUES ('DATABASE', 'UP', " + totalWaited + ", 'Resiliência e Auto-Correção concluídas')");
                        } catch (Exception e) { /* Ignorar se a tabela de telemetria falhar */ }
                    } catch (SQLException schemaEx) {
                        logger.warn("[OMEGA-SUPREME] Conectado, mas esquema ainda não detectado. Aguardando DDL de inicialização...");
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

    private void applySchemaFixes(Connection conn) {
        logger.info("[OMEGA-SUPREME] Iniciando Verificação de Integridade de Schema (Auto-Correção V35.1)...");
        String[] tables = {"students", "postgrad_students", "payments", "staff_members", "staff_member"};
        
        try (var stmt = conn.createStatement()) {
            for (String table : tables) {
                logger.info("[OMEGA-SUPREME] Analisando tabela: {}", table);
                
                // Colunas de Administração e Soft Delete (Resiliência Básica)
                executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE");
                executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS creator_name VARCHAR(255)");
                executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS creator_position VARCHAR(255)");
                executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS creator_photo_url TEXT");
                executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                
                // Colunas de Foto e Perfil
                if (table.equals("students") || table.equals("postgrad_students")) {
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS foto_matricula VARCHAR(255)");
                }

                // --- [ESPECÍFICO: PÓS-GRADUAÇÃO] ---
                if (table.equals("postgrad_students")) {
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS diploma_file_path VARCHAR(255)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS rg_cpf_file_path VARCHAR(255)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS proof_of_address_file_path VARCHAR(255)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS academic_transcript_file_path VARCHAR(255)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS phone VARCHAR(255)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS address TEXT");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS desired_course VARCHAR(255)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS graduation_institution VARCHAR(255)");
                }

                // --- [ESPECÍFICO: GRADUAÇÃO ROBUSTA] ---
                if (table.equals("students")) {
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS nacionalidade VARCHAR(100)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(50)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS sexo VARCHAR(20)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS numero_reservista VARCHAR(50)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS titulo_eleitor VARCHAR(50)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS is_estrangeiro BOOLEAN DEFAULT FALSE");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS forma_ingresso VARCHAR(50)");
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS tipo_cota VARCHAR(50)");
                }

                // --- [ESPECÍFICO: PAGAMENTOS] ---
                if (table.equals("payments")) {
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS student_photo_url TEXT");
                }

                // --- [ESPECÍFICO: STAFF] ---
                if (table.equals("staff_members")) {
                    executeAlter(stmt, table, "ADD COLUMN IF NOT EXISTS foto_url VARCHAR(255)");
                }
            }
            logger.info("[OMEGA-SUPREME] Auto-Correção V35.1 finalizada com sucesso!");
        } catch (SQLException e) {
            logger.error("[OMEGA-SUPREME] Erro ao aplicar correções de schema: {}", e.getMessage());
        }
    }

    private void executeAlter(java.sql.Statement stmt, String table, String sql) {
        try {
            stmt.execute("ALTER TABLE " + table + " " + sql);
        } catch (SQLException e) {
            // Ignorar erros comuns (como coluna já existente se o driver não suportar IF NOT EXISTS em alguns contextos)
            if (!e.getMessage().contains("already exists")) {
                logger.warn("[OMEGA-SUPREME] Aviso na tabela {}: {}", table, e.getMessage());
            }
        }
    }
}
