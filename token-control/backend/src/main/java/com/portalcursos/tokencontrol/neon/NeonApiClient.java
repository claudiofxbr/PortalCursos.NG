package com.portalcursos.tokencontrol.neon;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente somente-leitura da API de gestão do Neon (projeto, branches, endpoints). Desligado sem
 * NEON_API_KEY + NEON_PROJECT_ID. A chave nunca é logada nem devolvida; erros viram só "HTTP <status>".
 * Resposta em cache por 60 s para respeitar o rate limit da API.
 */
@Component
public class NeonApiClient {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final String apiKey;
    private final String projectId;
    private final String base;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();

    private NeonApiInfo cached;
    private Instant cachedAt = Instant.EPOCH;

    public NeonApiClient(@Value("${tokencontrol.neon.api-key:}") String apiKey,
            @Value("${tokencontrol.neon.project-id:}") String projectId,
            @Value("${tokencontrol.neon.api-base:https://console.neon.tech/api/v2}") String base, Clock clock) {
        this.apiKey = apiKey;
        this.projectId = projectId;
        this.base = base.replaceAll("/$", "");
        this.clock = clock;
    }

    public boolean enabled() {
        return !apiKey.isBlank() && !projectId.isBlank();
    }

    public synchronized NeonApiInfo fetch() {
        if (!enabled()) {
            return NeonApiInfo.disabled();
        }
        Instant now = clock.instant();
        if (cached != null && Duration.between(cachedAt, now).compareTo(TTL) < 0) {
            return cached;
        }
        NeonApiInfo info;
        try {
            JsonNode project = get("/projects/" + projectId).path("project");
            JsonNode branches = get("/projects/" + projectId + "/branches").path("branches");
            JsonNode endpoints = get("/projects/" + projectId + "/endpoints").path("endpoints");
            info = new NeonApiInfo(true, true, null, project(project), branches(branches), endpoints(endpoints));
        } catch (ApiException e) {
            info = NeonApiInfo.failed(e.getMessage());
        } catch (IOException | RuntimeException e) {
            info = NeonApiInfo.failed("indisponível");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            info = NeonApiInfo.failed("interrompido");
        }
        cached = info;
        cachedAt = now;
        return info;
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + apiKey).header("Accept", "application/json").GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new ApiException("HTTP " + res.statusCode());
        }
        return mapper.readTree(res.body());
    }

    private static NeonApiInfo.Project project(JsonNode p) {
        return new NeonApiInfo.Project(text(p, "name"), text(p, "region_id"), integer(p, "pg_version"),
                lng(p, "compute_time_seconds"), lng(p, "active_time_seconds"), lng(p, "written_data_bytes"),
                lng(p, "data_transfer_bytes"), lng(p, "synthetic_storage_size"), text(p, "consumption_period_start"),
                text(p, "consumption_period_end"));
    }

    private static List<NeonApiInfo.Branch> branches(JsonNode arr) {
        List<NeonApiInfo.Branch> out = new ArrayList<>();
        for (JsonNode b : arr) {
            out.add(new NeonApiInfo.Branch(text(b, "name"), text(b, "current_state"),
                    b.path("primary").asBoolean(false) || b.path("default").asBoolean(false), lng(b, "logical_size")));
        }
        return out;
    }

    private static List<NeonApiInfo.Endpoint> endpoints(JsonNode arr) {
        List<NeonApiInfo.Endpoint> out = new ArrayList<>();
        for (JsonNode e : arr) {
            out.add(new NeonApiInfo.Endpoint(text(e, "id"), text(e, "current_state"),
                    e.hasNonNull("autoscaling_limit_min_cu") ? e.get("autoscaling_limit_min_cu").asDouble() : null,
                    e.hasNonNull("autoscaling_limit_max_cu") ? e.get("autoscaling_limit_max_cu").asDouble() : null,
                    integer(e, "suspend_timeout_seconds"),
                    e.hasNonNull("pooler_enabled") ? e.get("pooler_enabled").asBoolean() : null));
        }
        return out;
    }

    private static String text(JsonNode n, String f) {
        return n.hasNonNull(f) ? n.get(f).asString() : null;
    }

    private static Long lng(JsonNode n, String f) {
        return n.hasNonNull(f) ? n.get(f).asLong() : null;
    }

    private static Integer integer(JsonNode n, String f) {
        return n.hasNonNull(f) ? n.get(f).asInt() : null;
    }

    private static final class ApiException extends RuntimeException {
        ApiException(String message) {
            super(message);
        }
    }
}
