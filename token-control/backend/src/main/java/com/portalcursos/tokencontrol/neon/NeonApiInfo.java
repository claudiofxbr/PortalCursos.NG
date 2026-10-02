package com.portalcursos.tokencontrol.neon;

import java.util.List;

/** Visão opcional via API de gestão do Neon. Campos ausentes na resposta viram null (tolerante a mudanças). */
public record NeonApiInfo(boolean enabled, boolean ok, String error, Project project, List<Branch> branches,
        List<Endpoint> endpoints) {

    public record Project(String name, String regionId, Integer pgVersion, Long computeTimeSeconds,
            Long activeTimeSeconds, Long writtenDataBytes, Long dataTransferBytes, Long syntheticStorageBytes,
            String consumptionPeriodStart, String consumptionPeriodEnd) {}

    public record Branch(String name, String state, boolean primary, Long logicalSizeBytes) {}

    public record Endpoint(String id, String state, Double minCu, Double maxCu, Integer suspendTimeoutSeconds,
            Boolean poolerEnabled) {}

    public static NeonApiInfo disabled() {
        return new NeonApiInfo(false, false, null, null, List.of(), List.of());
    }

    public static NeonApiInfo failed(String error) {
        return new NeonApiInfo(true, false, error, null, List.of(), List.of());
    }
}
