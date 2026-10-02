package com.portalcursos.tokencontrol.neon;

import java.util.List;

public record DbStatus(
        boolean connected,
        long latencyMs,
        String version,
        long databaseSizeBytes,
        Connections connections,
        DbEndpointInfo endpoint,
        Migrations migrations,
        List<TableInfo> tables,
        NeonApiInfo neonApi) {

    public record Connections(long total, long active, long max) {}

    /** status: OK | FAILED | UNKNOWN (mesma convenção do /api/health do PortalCursos.NG). */
    public record Migrations(String status, String latest, long failed) {}

    public record TableInfo(String name, long rows, long sizeBytes) {}

    public static DbStatus disconnected(long latencyMs, DbEndpointInfo endpoint, NeonApiInfo api) {
        return new DbStatus(false, latencyMs, null, 0, new Connections(0, 0, 0), endpoint,
                new Migrations("UNKNOWN", null, 0), List.of(), api);
    }
}
