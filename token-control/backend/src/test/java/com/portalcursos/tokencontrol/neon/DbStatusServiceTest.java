package com.portalcursos.tokencontrol.neon;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DbStatusServiceTest {

    private final NeonApiClient disabledApi = new NeonApiClient("", "", "http://x", Clock.systemUTC());

    @Test
    void bancoForaDoArViraDisconnectedSemLancar() {
        DbProbe broken = (e, a) -> {
            throw new DataAccessResourceFailureException("connection refused");
        };
        DbStatus s = new DbStatusService(broken, disabledApi, "jdbc:postgresql://ep-x-1-pooler.sa-east-1.aws.neon.tech/db")
                .status();
        assertThat(s.connected()).isFalse();
        assertThat(s.migrations().status()).isEqualTo("UNKNOWN");
        assertThat(s.endpoint().pooled()).isTrue();
        assertThat(s.neonApi().enabled()).isFalse();
    }

    @Test
    void repassaResultadoDaSondaEEndpointDaUrl() {
        DbProbe ok = (e, a) -> new DbStatus(true, 7, "16.1", 1000, new DbStatus.Connections(2, 1, 100), e,
                new DbStatus.Migrations("OK", "1", 0), java.util.List.of(), a);
        DbStatus s = new DbStatusService(ok, disabledApi, "jdbc:postgresql://ep-a-1.us-east-2.aws.neon.tech/db").status();
        assertThat(s.connected()).isTrue();
        assertThat(s.endpoint().endpointId()).isEqualTo("ep-a-1");
        assertThat(s.endpoint().pooled()).isFalse();
    }
}
