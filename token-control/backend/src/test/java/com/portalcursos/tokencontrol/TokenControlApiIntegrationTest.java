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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void limpaConsumo() {
        jdbc.update("delete from token_usage_entries"); // isolamento: cada teste parte do banco sem consumo
    }

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
    void mesAtualSomaDoDia1ComLimiteEstimadoEDepoisConfigurado() throws Exception {
        Instant now = Instant.now();
        mvc.perform(post("/api/tokens/usage").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(batch(entry("mes-1", now.minusSeconds(120), "proj-mes", "claude-sonnet-5-5", 1000, 500, 0, 9999))))
                .andExpect(status().isOk());

        // sem orçamento mensal → referência estimada (semanal x dias/7), sinalizada
        mvc.perform(get("/api/tokens/month").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitEstimated").value(true))
                .andExpect(jsonPath("$.used").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1500)))
                .andExpect(jsonPath("$.period.daysInMonth").value(org.hamcrest.Matchers.greaterThanOrEqualTo(28)))
                .andExpect(jsonPath("$.daily.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(28)))
                .andExpect(jsonPath("$.remaining").isNumber())
                .andExpect(jsonPath("$.byProcess[?(@.name=='proj-mes')]").exists());

        String cfg = """
            {"planName":"Claude Code Pro","resetDayOfWeek":1,"resetTime":"00:00","timezone":"America/Sao_Paulo",
             "weeklyLimitTokens":50000000,"monthlyLimitTokens":200000000,"countCacheReads":false}""";
        mvc.perform(put("/api/tokens/config").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(cfg)).andExpect(status().isOk()).andExpect(jsonPath("$.monthlyLimitTokens").value(200000000));
        mvc.perform(get("/api/tokens/month").header("X-API-Key", KEY))
                .andExpect(jsonPath("$.limitEstimated").value(false))
                .andExpect(jsonPath("$.limit").value(200000000));
        mvc.perform(get("/api/tokens/month")).andExpect(status().isUnauthorized());

        // volta ao padrão (limite mensal null) para não vazar estado
        mvc.perform(put("/api/tokens/config").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(cfg.replace(",\"monthlyLimitTokens\":200000000", ""))).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyLimitTokens").doesNotExist());
    }

    @Test
    void torreDeControleListaProcessosEReagePeloEstadoReal() throws Exception {
        mvc.perform(get("/api/tokens/tower")).andExpect(status().isUnauthorized());

        // banco de testes sem consumo: coletor na fila; H2 não é Postgres → banco "crítico"; geral crítico
        mvc.perform(get("/api/tokens/tower").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='collector')].status").value("queued"))
                .andExpect(jsonPath("$.items[?(@.id=='database')].status").value("danger"))
                .andExpect(jsonPath("$.items[?(@.id=='api')].status").value("ok"))
                .andExpect(jsonPath("$.items[?(@.id=='cycle-week')]").exists())
                .andExpect(jsonPath("$.items[?(@.id=='cycle-month')]").exists())
                .andExpect(jsonPath("$.overall").value("danger"));

        // após um envio: coletor OK e o processo aparece "em andamento"
        mvc.perform(post("/api/tokens/usage").header("X-API-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content(batch(entry("torre-1", Instant.now().minusSeconds(60), "proj-torre", "claude-sonnet-5-5", 10, 5, 0, 0))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tokens/tower").header("X-API-Key", KEY))
                .andExpect(jsonPath("$.items[?(@.id=='collector')].status").value("ok"))
                .andExpect(jsonPath("$.items[?(@.id=='work-proj-torre')].category").value("work"))
                .andExpect(jsonPath("$.counts.active").value(1));
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
