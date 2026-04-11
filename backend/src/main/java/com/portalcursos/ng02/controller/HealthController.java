package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@org.springframework.web.bind.annotation.CrossOrigin(origins = "*", allowedHeaders = "*")
public class HealthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String VERSION = "V30.0-SUPREME";

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> checkHealth() {
        long startTime = System.currentTimeMillis();
        String status = "UP";
        String message = "Sistema Operacional - Resiliência V30.0-SUPREME";
        Long latency = null;
        Map<String, Object> diagnostics = new HashMap<>();

        boolean dbIsReady = com.portalcursos.ng02.config.DatabaseResilienceComponent.isDatabaseReady();
        
        try {
            // Teste de Conectividade real para conferir saúde do pool
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            latency = System.currentTimeMillis() - startTime;
            diagnostics.put("database", "CONNECTED");
            
            if (!dbIsReady) {
                // Sincronização forçada se a query funcionou mas a flag estava falsa
                status = "SYNCHRONIZING";
                message = "Conexão estabelecida. Ajustando estado interno...";
            }
        } catch (Exception e) {
            status = dbIsReady ? "PARTIAL" : "BOOTING";
            message = dbIsReady ? "Database connection transient failure" : "Aguardando Cloud Neon (Cold Start)...";
            diagnostics.put("database", "OFFLINE_OR_BUSY");
            diagnostics.put("db_error", e.getMessage());
        }

        // Diagnósticos de Infraestrutura Supreme
        diagnostics.put("protocol", "V30.0-SUPREME");
        diagnostics.put("environment", "DEVELOPMENT/PRODUCTION-SYNC");
        diagnostics.put("max_memory_mb", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptime = String.format("%d min, %d sec", 
                (uptimeMs / 1000) / 60, 
                (uptimeMs / 1000) % 60);

        HealthResponse response = HealthResponse.builder()
                .status(status)
                .databaseLatencyMs(latency)
                .serverTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .uptime(uptime)
                .message(message)
                .version(VERSION)
                .diagnostics(diagnostics)
                .build();

        if ("BOOTING".equals(status)) {
            return ResponseEntity.accepted().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
