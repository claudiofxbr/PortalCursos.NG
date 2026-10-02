package com.portalcursos.tokencontrol;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Fluxo real de ponta a ponta no backend: Flyway V1 + JPA + filtro de chave + serviços (H2 modo Postgres). */
@SpringBootTest
@AutoConfigureMockMvc
class TokenControlApiIntegrationTest {

    private static final String KEY = "test-key-123";
    @Autowired MockMvc mvc;

    private String entry(String id, Instant at, String process, String model, long in, long out, long cc, long cr) {
        return """
            {"messageId":"%s","occurredAt":"%s","sessionId":"s1","process":"%s","model":"%s",
             "inputTokens":%d,"outputTokens":%d,"cacheCreationTokens":%d,"cacheReadTokens":%d}"""
                .formatted(id, at, process, model, in, out, cc, cr);
    }

    private String batch(String... entries) {
        return "{\"entries\":[" + String.join(",", entries) + "]}";
    }

    @Test
    void semChaveRetorna401() throws Exception {
        mvc.perform(get("/api/tokens/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tokens/summary").header("X-API-Key", "errada")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void ingestaoEIdempotenteEResumoSomaOCicloAtual() throws Exception {
        Instant now = Instant.now();
        String body = batch(
                entry("m-1", now.minusSeconds(60), "projeto-a", "claude-sonnet-5-5", 100, 50, 10, 5000),
                entry("m-2", now.minusSeconds(30), "projeto-b", "claude-opus-5-5", 200, 0, 0, 0),
                entry("m-1", now.minusSeconds(60), "projeto-a", "claude-sonnet-5-5", 100, 50, 10, 5000));

        mvc.perform(post("/api/tokens/usage").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(3))
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.duplicates").value(1));

        // reenvio do mesmo lote não duplica
        mvc.perform(post("/api/tokens/usage").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.inserted").value(0));

        // cache_read fora da conta por padrão: (100+50+10) + 200 = 360
        mvc.perform(get("/api/tokens/summary").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(360))
                .andExpect(jsonPath("$.totals.cacheRead").value(5000))
                .andExpect(jsonPath("$.totals.messages").value(2))
                .andExpect(jsonPath("$.daily", hasSize(7)))
                .andExpect(jsonPath("$.byProcess", hasSize(2)))
                .andExpect(jsonPath("$.byProcess[0].name").value("projeto-b"))
                .andExpect(jsonPath("$.byModel", hasSize(2)))
                .andExpect(jsonPath("$.last5hTokens").value(360));

        mvc.perform(get("/api/tokens/history?cycles=3").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].current").value(true))
                .andExpect(jsonPath("$[2].used").value(360));
    }

    @Test
    void dbStatusDegradaParaDisconnectedSem500QuandoNaoEPostgres() throws Exception {
        // H2 não tem as views de catálogo do Postgres: a sonda falha e o endpoint responde 200 com connected=false
        mvc.perform(get("/api/tokens/db").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.neonApi.enabled").value(false));
        mvc.perform(get("/api/tokens/db")).andExpect(status().isUnauthorized());
    }

    @Test
    void validacaoRejeitaPayloadInvalido() throws Exception {
        String bad = batch(entry("m-bad", Instant.now(), "p", "m", -5, 0, 0, 0));
        mvc.perform(post("/api/tokens/usage").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(bad)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tokens/usage").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/tokens/usage").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("não é json")).andExpect(status().isBadRequest());
    }

    @Test
    void configPodeSerAtualizadaEValidaFuso() throws Exception {
        String ok = """
            {"planName":"Claude Code Pro","resetDayOfWeek":5,"resetTime":"14:30","timezone":"America/Sao_Paulo",
             "weeklyLimitTokens":12345678,"countCacheReads":true}""";
        mvc.perform(put("/api/tokens/config").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(ok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetTime").value("14:30"))
                .andExpect(jsonPath("$.weeklyLimitTokens").value(12345678));

        mvc.perform(put("/api/tokens/config").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(ok.replace("America/Sao_Paulo", "Marte/Olympus")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/tokens/config").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(ok.replace("14:30", "25:99"))).andExpect(status().isBadRequest());

        // restaura padrão para não vazar estado para outros testes
        mvc.perform(put("/api/tokens/config").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(ok.replace("\"resetDayOfWeek\":5", "\"resetDayOfWeek\":1").replace("14:30", "00:00")
                        .replace("12345678", "50000000").replace("true", "false")))
                .andExpect(status().isOk());
    }
}
