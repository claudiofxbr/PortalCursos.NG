package com.portalcursos.tokencontrol.neon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DbStatusService {

    private static final Logger log = LoggerFactory.getLogger(DbStatusService.class);

    private final DbProbe probe;
    private final NeonApiClient neonApi;
    private final DbEndpointInfo endpoint;

    public DbStatusService(DbProbe probe, NeonApiClient neonApi,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.probe = probe;
        this.neonApi = neonApi;
        this.endpoint = DbEndpointInfo.parse(datasourceUrl);
    }

    /** Nunca lança: banco fora do ar vira connected=false (o dashboard mostra o alerta). */
    public DbStatus status() {
        NeonApiInfo api = neonApi.fetch();
        long t0 = System.nanoTime();
        try {
            return probe.probe(endpoint, api);
        } catch (RuntimeException e) {
            log.warn("Sonda do banco falhou: {}", e.getClass().getSimpleName());
            return DbStatus.disconnected((System.nanoTime() - t0) / 1_000_000, endpoint, api);
        }
    }
}
