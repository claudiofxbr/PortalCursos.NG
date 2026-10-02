package com.portalcursos.tokencontrol.neon;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NeonApiClientTest {

    private HttpServer server;
    private final List<String> auth = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            calls.incrementAndGet();
            auth.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            String path = ex.getRequestURI().getPath();
            String body;
            if (path.endsWith("/branches")) {
                body = "{\"branches\":[{\"name\":\"main\",\"current_state\":\"ready\",\"primary\":true,\"logical_size\":1048576}]}";
            } else if (path.endsWith("/endpoints")) {
                body = "{\"endpoints\":[{\"id\":\"ep-1\",\"current_state\":\"idle\",\"autoscaling_limit_min_cu\":0.25,"
                        + "\"autoscaling_limit_max_cu\":2,\"suspend_timeout_seconds\":300,\"pooler_enabled\":true}]}";
            } else {
                body = "{\"project\":{\"name\":\"portal\",\"region_id\":\"aws-sa-east-1\",\"pg_version\":17,"
                        + "\"compute_time_seconds\":3600,\"written_data_bytes\":2048}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private NeonApiClient client(String key, String project, Clock clock) {
        return new NeonApiClient(key, project, base, clock);
    }

    @Test
    void semChaveOuProjetoFicaDesligadoENaoFazRequisicao() {
        assertThat(client("", "p", Clock.systemUTC()).fetch().enabled()).isFalse();
        assertThat(client("k", "", Clock.systemUTC()).fetch().enabled()).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void leProjetoBranchesEEndpointsEEnviaBearer() {
        NeonApiInfo info = client("napi_x", "p1", Clock.systemUTC()).fetch();
        assertThat(info.ok()).isTrue();
        assertThat(info.project().name()).isEqualTo("portal");
        assertThat(info.project().pgVersion()).isEqualTo(17);
        assertThat(info.project().computeTimeSeconds()).isEqualTo(3600L);
        assertThat(info.project().dataTransferBytes()).isNull(); // campo ausente → null, sem quebrar
        assertThat(info.branches()).singleElement().satisfies(b -> {
            assertThat(b.primary()).isTrue();
            assertThat(b.logicalSizeBytes()).isEqualTo(1_048_576L);
        });
        assertThat(info.endpoints().get(0).minCu()).isEqualTo(0.25);
        assertThat(info.endpoints().get(0).poolerEnabled()).isTrue();
        assertThat(auth).allMatch("Bearer napi_x"::equals);
    }

    @Test
    void erroHttpNaoVazaChaveEFicaEmCacheDurante60s() {
        status = 401;
        Instant t0 = Instant.parse("2026-10-02T10:00:00Z");
        NeonApiClient c = client("napi_secreta", "p1", Clock.fixed(t0, ZoneOffset.UTC));
        NeonApiInfo info = c.fetch();
        assertThat(info.ok()).isFalse();
        assertThat(info.error()).isEqualTo("HTTP 401").doesNotContain("napi_secreta");
        int after = calls.get();
        c.fetch();
        assertThat(calls.get()).isEqualTo(after); // cache
    }

    @Test
    void servidorForaDoArViraIndisponivelSemExcecao() {
        server.stop(0);
        NeonApiInfo info = client("k", "p1", Clock.systemUTC()).fetch();
        assertThat(info.ok()).isFalse();
        assertThat(info.error()).isEqualTo("indisponível");
    }
}
