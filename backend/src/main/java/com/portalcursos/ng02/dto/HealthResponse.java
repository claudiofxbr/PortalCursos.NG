package com.portalcursos.ng02.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthResponse {
    private String status;
    private Long databaseLatencyMs;
    private String serverTime;
    private String uptime;
    private String message;
    private String version;
    private Map<String, Object> diagnostics;
}
