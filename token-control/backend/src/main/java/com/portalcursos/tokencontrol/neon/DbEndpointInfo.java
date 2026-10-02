package com.portalcursos.tokencontrol.neon;

import java.net.URI;

/** Dados NÃO sensíveis extraídos da URL JDBC (nunca usuário/senha/query). */
public record DbEndpointInfo(boolean neon, boolean pooled, String endpointId, String region) {

    public static DbEndpointInfo parse(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            return new DbEndpointInfo(false, false, null, null);
        }
        String host;
        try {
            host = URI.create(jdbcUrl.substring("jdbc:".length())).getHost();
        } catch (IllegalArgumentException e) {
            return new DbEndpointInfo(false, false, null, null);
        }
        if (host == null || !host.endsWith(".neon.tech")) {
            return new DbEndpointInfo(false, false, null, null);
        }
        String[] labels = host.split("\\.");
        boolean pooled = labels[0].endsWith("-pooler");
        String endpoint = pooled ? labels[0].substring(0, labels[0].length() - "-pooler".length()) : labels[0];
        // hosts atuais têm um segmento extra (ep-xxx.c-2.sa-east-1.aws.neon.tech): a região é o rótulo no formato aa-bbbb-N
        String region = null;
        for (int i = 1; i < labels.length; i++) {
            if (labels[i].matches("[a-z]{2}-[a-z]+-\\d+")) {
                region = labels[i];
                break;
            }
        }
        return new DbEndpointInfo(true, pooled, endpoint, region);
    }
}
