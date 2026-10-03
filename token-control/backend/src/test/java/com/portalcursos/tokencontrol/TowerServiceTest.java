package com.portalcursos.tokencontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.portalcursos.tokencontrol.dto.ConfigDto;
import com.portalcursos.tokencontrol.dto.MonthSummaryResponse;
import com.portalcursos.tokencontrol.dto.SummaryResponse;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Projection;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Totals;
import com.portalcursos.tokencontrol.dto.TowerResponse;
import com.portalcursos.tokencontrol.dto.TowerResponse.Item;
import com.portalcursos.tokencontrol.model.PlanConfig;
import com.portalcursos.tokencontrol.neon.DbEndpointInfo;
import com.portalcursos.tokencontrol.neon.DbStatus;
import com.portalcursos.tokencontrol.neon.DbStatusService;
import com.portalcursos.tokencontrol.neon.NeonApiInfo;
import com.portalcursos.tokencontrol.repository.ProcessActivity;
import com.portalcursos.tokencontrol.repository.UsageRepository;
import com.portalcursos.tokencontrol.service.ConfigService;
import com.portalcursos.tokencontrol.service.SummaryService;
import com.portalcursos.tokencontrol.service.TowerService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TowerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T18:00:00Z");

    private SummaryService summary;
    private ConfigService configService;
    private DbStatusService dbService;
    private UsageRepository repo;
    private PlanConfig config;
    private TowerService tower;

    @BeforeEach
    void setup() {
        summary = mock(SummaryService.class);
        configService = mock(ConfigService.class);
        dbService = mock(DbStatusService.class);
        repo = mock(UsageRepository.class);
        config = new PlanConfig("Claude Code Pro", 1, LocalTime.MIDNIGHT, "America/Sao_Paulo", 80_000_000L, false, NOW);
        config.apply("Claude Code Pro", 1, LocalTime.MIDNIGHT, "America/Sao_Paulo", 80_000_000L, 300_000_000L, false, NOW);
        when(configService.current()).thenReturn(config);
        when(summary.summary(any())).thenReturn(week(10, null));
        when(summary.month(any())).thenReturn(month(10, null));
        when(dbService.status()).thenReturn(db(true, 20, "OK", enabledApi()));
        when(repo.lastIngestedAt()).thenReturn(NOW.minus(Duration.ofHours(1)));
        when(repo.lastOccurredAt()).thenReturn(NOW.minus(Duration.ofMinutes(50)));
        when(repo.activityByProcess(any(), any())).thenReturn(List.of());
        tower = new TowerService(summary, configService, dbService, repo);
    }

    private ConfigDto cfg() {
        return new ConfigDto("Claude Code Pro", 1, "00:00", "America/Sao_Paulo", 80_000_000L, 300_000_000L, false);
    }

    private SummaryResponse week(double pct, Projection projection) {
        long limit = 80_000_000L;
        return new SummaryResponse(cfg(), new SummaryResponse.Cycle(NOW, NOW.plusSeconds(300_000), 273_600, 40), limit,
                Math.round(limit * pct / 100), limit - Math.round(limit * pct / 100), pct, new Totals(1, 1, 1, 1, 10),
                projection, 0, List.of(), List.of(), List.of());
    }

    private MonthSummaryResponse month(double pct, Projection projection) {
        long limit = 300_000_000L;
        return new MonthSummaryResponse(cfg(), new MonthSummaryResponse.Period(NOW, NOW.plusSeconds(900_000), 31, 2, 900_000, 6),
                limit, false, Math.round(limit * pct / 100), limit - Math.round(limit * pct / 100), pct,
                new Totals(1, 1, 1, 1, 10), projection, List.of(), List.of(), List.of());
    }

    private NeonApiInfo enabledApi() {
        return new NeonApiInfo(true, true, null, null, List.of(), List.of());
    }

    private DbStatus db(boolean connected, long latency, String migrations, NeonApiInfo api) {
        return new DbStatus(connected, latency, "16", 1000, new DbStatus.Connections(1, 1, 100),
                new DbEndpointInfo(true, true, "ep-x", "sa-east-1"), new DbStatus.Migrations(migrations, "2", 0), List.of(), api);
    }

    private Item item(TowerResponse r, String id) {
        return r.items().stream().filter(i -> i.id().equals(id)).findFirst().orElse(null);
    }

    @Test
    void sistemaSaudavelFicaOkEMantemRecorrentesVisiveis() {
        TowerResponse r = tower.tower(NOW);
        assertThat(r.overall()).isEqualTo("ok");
        for (String id : List.of("api", "database", "collector", "cycle-week", "cycle-month")) {
            assertThat(item(r, id)).as(id).isNotNull();
            assertThat(item(r, id).status()).as(id).isEqualTo("ok");
        }
        assertThat(item(r, "collector").category()).isEqualTo("auto");
        assertThat(item(r, "database").category()).isEqualTo("health");
        assertThat(r.counts().danger()).isZero();
    }

    @Test
    void coletorPorIdadeDoUltimoEnvio() {
        when(repo.lastIngestedAt()).thenReturn(NOW.minus(Duration.ofHours(10)));
        assertThat(item(tower.tower(NOW), "collector").status()).isEqualTo("warn");
        when(repo.lastIngestedAt()).thenReturn(NOW.minus(Duration.ofDays(3)));
        TowerResponse r = tower.tower(NOW);
        assertThat(item(r, "collector").status()).isEqualTo("danger");
        assertThat(item(r, "collector").detail()).contains("3d 0h").contains("enviar-consumo.ps1");
        assertThat(r.overall()).isEqualTo("danger");
    }

    @Test
    void semNenhumEnvioColetorFicaNaFilaENaoDegradaOGeral() {
        when(repo.lastIngestedAt()).thenReturn(null);
        TowerResponse r = tower.tower(NOW);
        assertThat(item(r, "collector").status()).isEqualTo("queued");
        assertThat(r.overall()).isEqualTo("ok");
    }

    @Test
    void bancoDesconectadoOuMigrationFalhaSaoCriticosELentidaoEAtencao() {
        when(dbService.status()).thenReturn(DbStatus.disconnected(5, new DbEndpointInfo(true, true, "ep", "sa"), enabledApi()));
        TowerResponse down = tower.tower(NOW);
        assertThat(item(down, "database").status()).isEqualTo("danger");
        assertThat(down.overall()).isEqualTo("danger");

        when(dbService.status()).thenReturn(db(true, 20, "FAILED", enabledApi()));
        assertThat(item(tower.tower(NOW), "database").status()).isEqualTo("danger");

        when(dbService.status()).thenReturn(db(true, 1500, "OK", enabledApi()));
        assertThat(item(tower.tower(NOW), "database").status()).isEqualTo("warn");
    }

    @Test
    void ciclosUsamOsMesmosLimiares70e90DoDashboard() {
        when(summary.summary(any())).thenReturn(week(72, null));
        when(summary.month(any())).thenReturn(month(95, null));
        TowerResponse r = tower.tower(NOW);
        assertThat(item(r, "cycle-week").status()).isEqualTo("warn");
        assertThat(item(r, "cycle-month").status()).isEqualTo("danger");
        assertThat(item(r, "cycle-week").detail()).contains("57,6 mi de 80 mi").contains("72%");
        assertThat(TowerService.severity(69.9)).isEqualTo("ok");
        assertThat(TowerService.severity(70)).isEqualTo("warn");
        assertThat(TowerService.severity(90)).isEqualTo("danger");
    }

    @Test
    void projecaoQueEstouraViraAlertaNaFila() {
        Projection p = new Projection(120_000_000L, 150, 20_000_000L, true, Instant.parse("2026-10-04T15:00:00Z"));
        when(summary.summary(any())).thenReturn(week(50, p));
        TowerResponse r = tower.tower(NOW);
        Item alert = item(r, "alert-projection-week");
        assertThat(alert.category()).isEqualTo("queue");
        assertThat(alert.status()).isEqualTo("warn");
        assertThat(alert.detail()).contains("120 mi").contains("150%").contains("esgota em 04/10 12:00");
        assertThat(r.overall()).isEqualTo("warn");

        when(summary.summary(any())).thenReturn(week(100, p));
        assertThat(item(tower.tower(NOW), "alert-projection-week").status()).isEqualTo("danger");
    }

    @Test
    void emAndamentoListaProcessosAtivosNasUltimas5hOrdenadosPorConsumo() {
        List<ProcessActivity> live = new ArrayList<>();
        live.add(new ProcessActivity("pequeno", 10, 0, 0, 0, 1, NOW.minus(Duration.ofMinutes(5))));
        live.add(new ProcessActivity("grande", 5000, 2000, 100, 99999, 40, NOW.minus(Duration.ofHours(2))));
        when(repo.activityByProcess(any(), any())).thenReturn(live);
        List<Item> work = tower.tower(NOW).items().stream().filter(i -> i.category().equals("work")).toList();
        assertThat(work).extracting(Item::label).containsExactly("grande", "pequeno");
        assertThat(work.get(0).status()).isEqualTo("active");
        assertThat(work.get(0).detail()).contains("7.100 tokens").contains("40 mensagens").contains("há 2h 0min"); // cache read fora
    }

    @Test
    void pendenciasDeConfiguracaoSoAparecemEnquantoExistem() {
        config.apply("Claude Code Pro", 1, LocalTime.MIDNIGHT, "America/Sao_Paulo", 50_000_000L, null, false, NOW);
        when(dbService.status()).thenReturn(db(true, 20, "OK", NeonApiInfo.disabled()));
        TowerResponse r = tower.tower(NOW);
        assertThat(item(r, "pending-weekly-limit").status()).isEqualTo("queued");
        assertThat(item(r, "pending-monthly-limit").status()).isEqualTo("queued");
        assertThat(item(r, "pending-neon-api").label()).contains("desligada");

        config.apply("Claude Code Pro", 1, LocalTime.MIDNIGHT, "America/Sao_Paulo", 80_000_000L, 300_000_000L, false, NOW);
        when(dbService.status()).thenReturn(db(true, 20, "OK", enabledApi()));
        TowerResponse ok = tower.tower(NOW);
        assertThat(item(ok, "pending-weekly-limit")).isNull();
        assertThat(item(ok, "pending-monthly-limit")).isNull();
        assertThat(item(ok, "pending-neon-api")).isNull();
    }

    @Test
    void erroDaApiDoNeonViraAtencaoComOMotivo() {
        when(dbService.status()).thenReturn(db(true, 20, "OK", NeonApiInfo.failed("HTTP 404")));
        Item i = item(tower.tower(NOW), "pending-neon-api");
        assertThat(i.status()).isEqualTo("warn");
        assertThat(i.detail()).contains("HTTP 404").contains("ID do projeto");
    }

    @Test
    void formatadoresEmPortugues() {
        assertThat(TowerService.human(Duration.ofSeconds(30))).isEqualTo("menos de 1 min");
        assertThat(TowerService.human(Duration.ofMinutes(45))).isEqualTo("45min");
        assertThat(TowerService.human(Duration.ofMinutes(125))).isEqualTo("2h 5min");
        assertThat(TowerService.human(Duration.ofHours(52))).isEqualTo("2d 4h");
        assertThat(TowerService.tokens(950)).isEqualTo("950");
        assertThat(TowerService.tokens(12_500)).isEqualTo("12,5 mil");
        assertThat(TowerService.tokens(3_400_000)).isEqualTo("3,4 mi");
    }
}
