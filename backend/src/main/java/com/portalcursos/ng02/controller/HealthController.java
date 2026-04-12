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

    private static final String VERSION = "V30.9-SUPREME-FINAL";

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> checkHealth() {
        long startTime = System.currentTimeMillis();
        String status = "UP";
        String message = "PortalCursos OMEGA-SUPREME: Ativo e Resiliente";
        Long latency = null;
        Map<String, Object> diagnostics = new HashMap<>();

        boolean dbIsReady = com.portalcursos.ng02.config.DatabaseResilienceComponent.isDatabaseReady();
        
        try {
            // Teste de Conectividade real
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            latency = System.currentTimeMillis() - startTime;
            diagnostics.put("database", "CONNECTED");
            
            if (!dbIsReady) {
                // Sincronização em curso
                status = "SYNCHRONIZING";
                message = "Esquema OMEGA detectado. Finalizando aquecimento...";
            }
        } catch (Exception e) {
            status = dbIsReady ? "PARTIAL" : "BOOTING";
            message = dbIsReady ? "Oscilação temporária de rede Cloud" : "Iniciando Infraestrutura (Cold Start Neon)...";
            diagnostics.put("database", "OFFLINE_OR_BUSY");
            diagnostics.put("db_error", e.getMessage());
        }

        // Diagnósticos Supreme
        diagnostics.put("protocol", "OMEGA-SUPREME");
        diagnostics.put("cloud_provider", "RENDER/HOSTINGER/NEON");
        
        HealthResponse response = HealthResponse.builder()
                .status(status)
                .databaseLatencyMs(latency)
                .serverTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .uptime(ManagementFactory.getRuntimeMXBean().getUptime() / 1000 + "s")
                .message(message)
                .version(VERSION)
                .diagnostics(diagnostics)
                .build();

        if ("BOOTING".equals(status)) {
            // Retornamos 503 para indicar que o serviço não está pronto para tráfego total ainda
            return ResponseEntity.status(503).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
