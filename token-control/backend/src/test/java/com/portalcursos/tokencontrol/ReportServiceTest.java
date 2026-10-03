package com.portalcursos.tokencontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.portalcursos.tokencontrol.dto.ConfigDto;
import com.portalcursos.tokencontrol.dto.MonthSummaryResponse;
import com.portalcursos.tokencontrol.dto.ReportResponse;
import com.portalcursos.tokencontrol.dto.ReportResponse.Section;
import com.portalcursos.tokencontrol.dto.SummaryResponse;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Day;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Projection;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Slice;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Totals;
import com.portalcursos.tokencontrol.dto.TowerResponse;
import com.portalcursos.tokencontrol.dto.TowerResponse.Counts;
import com.portalcursos.tokencontrol.dto.TowerResponse.Item;
import com.portalcursos.tokencontrol.service.ReportService;
import com.portalcursos.tokencontrol.service.SummaryService;
import com.portalcursos.tokencontrol.service.TowerService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T15:00:00Z");

    private SummaryService summary;
    private TowerService towerService;
    private ReportService report;
    private Totals totals = new Totals(1_000, 2_000, 3_000, 4_000, 48);
    private List<Slice> processes = List.of(new Slice("PortalCursos.NG", 600, 30, 40.0), new Slice("token-control", 400, 18, 27.0));
    private Projection weekProjection = new Projection(60_000_000L, 75, 9_000_000L, false, null);
    private TowerResponse tower = ok();

    @BeforeEach
    void setup() {
        summary = mock(SummaryService.class);
        towerService = mock(TowerService.class);
        report = new ReportService(summary, towerService);
        refresh();
    }

    private void refresh() {
        when(summary.summary(any())).thenReturn(week());
        when(summary.month(any())).thenReturn(month());
        when(towerService.tower(any())).thenReturn(tower);
    }

    private ConfigDto cfg() {
        return new ConfigDto("Claude Code Pro", 1, "00:00", "America/Sao_Paulo", 80_000_000L, 300_000_000L, false);
    }

    private SummaryResponse week() {
        List<Day> daily = List.of(new Day(1, LocalDate.of(2026, 9, 28), 1_000_000, 1_000_000), new Day(2, LocalDate.of(2026, 9, 29), 9_000_000, 10_000_000));
        return new SummaryResponse(cfg(), new SummaryResponse.Cycle(NOW, NOW.plusSeconds(273_600), 273_600, 40), 80_000_000L, 32_000_000L,
                48_000_000L, 40, totals, weekProjection, 500_000, daily, List.of(), List.of());
    }

    private MonthSummaryResponse month() {
        return new MonthSummaryResponse(cfg(), new MonthSummaryResponse.Period(NOW, NOW.plusSeconds(2_000_000), 31, 3, 2_000_000, 10),
                300_000_000L, true, 18_000_000L, 282_000_000L, 6, totals, null, List.of(), processes,
                List.of(new Slice("claude-sonnet-5-5", 1000, 48, 100)));
    }

    private static TowerResponse ok() {
        return new TowerResponse(NOW, "ok", new Counts(5, 0, 0, 0, 0), List.of(
                new Item("collector", "auto", "Coletor", "ok", "Último envio há 1h 0min")));
    }

    private Section section(ReportResponse r, String title) {
        return r.sections().stream().filter(s -> s.title().startsWith(title)).findFirst().orElseThrow();
    }

    @Test
    void estruturaEmPortuguesComSeisSecoesNaOrdem() {
        ReportResponse r = report.report(NOW);
        assertThat(r.title()).isEqualTo("Relatório de análise — Controle de Tokens Claude Code");
        assertThat(r.sections()).extracting(Section::title).containsExactly("Resumo executivo", "Ciclo semanal", "Mês atual",
                "Quem consome (mês atual)", "Pontos de atenção (Torre de Controle)", "Recomendações");
        Section exec = section(r, "Resumo executivo");
        assertThat(exec.paragraphs().get(0)).contains("operação normal");
        assertThat(exec.paragraphs().get(1)).contains("32 mi de 80 mi (40%)").contains("restam 48 mi");
        assertThat(exec.paragraphs().get(2)).contains("Mês atual").contains("(estimado)");
        assertThat(exec.paragraphs().get(3)).contains("48 mensagem(ns)").contains("Último envio há 1h 0min");
    }

    @Test
    void semanaTrazProjecaoRitmoMaximoEDiaMaisIntenso() {
        Section w = section(report.report(NOW), "Ciclo semanal");
        assertThat(w.bullets()).anyMatch(b -> b.contains("Projeção até o reset: 60 mi (75% do orçamento)") && b.contains("dentro do orçamento"));
        assertThat(w.bullets()).anyMatch(b -> b.contains("Ritmo máximo para fechar dentro do orçamento: 15,2 mi por dia"));
        assertThat(w.bullets()).anyMatch(b -> b.contains("Dia mais intenso: 29/09 com 9 mi"));
    }

    @Test
    void mesSemBaseEstatisticaDizIssoEMarcaLimiteEstimado() {
        Section m = section(report.report(NOW), "Mês atual");
        assertThat(m.bullets()).anyMatch(b -> b.contains("limite estimado"));
        assertThat(m.bullets()).anyMatch(b -> b.contains("sem base estatística"));
    }

    @Test
    void projecaoQueEstouraGeraRecomendacaoComRitmoPermitido() {
        weekProjection = new Projection(120_000_000L, 150, 20_000_000L, true, Instant.parse("2026-10-04T15:00:00Z"));
        refresh();
        ReportResponse r = report.report(NOW);
        assertThat(section(r, "Ciclo semanal").bullets()).anyMatch(b -> b.contains("VAI ESTOURAR") && b.contains("04/10 12:00"));
        assertThat(section(r, "Recomendações").bullets())
                .anyMatch(b -> b.contains("Ritmo semanal acima do orçamento") && b.contains("20 mi por dia") && b.contains("15,2 mi por dia"));
    }

    @Test
    void concentracaoEmUmProcessoGeraRecomendacao() {
        processes = List.of(new Slice("PortalCursos.NG", 700, 30, 70.0), new Slice("outro", 300, 5, 30.0));
        refresh();
        assertThat(section(report.report(NOW), "Recomendações").bullets())
                .anyMatch(b -> b.contains("\"PortalCursos.NG\" concentra 70%"));
        // um único processo não é "concentração" relevante
        processes = List.of(new Slice("unico", 1000, 35, 100.0));
        refresh();
        assertThat(section(report.report(NOW), "Recomendações").bullets()).noneMatch(b -> b.contains("concentra"));
    }

    @Test
    void leituraDeCacheDominanteFicaForaDaContaEOrienta() {
        totals = new Totals(100, 100, 100, 9_700, 48);
        refresh();
        assertThat(section(report.report(NOW), "Recomendações").bullets())
                .anyMatch(b -> b.contains("Leituras de cache são 97%") && b.contains("Contar leituras de cache"));
        Section consumers = section(report.report(NOW), "Quem consome");
        assertThat(consumers.bullets()).anyMatch(b -> b.contains("leitura de cache 97%"));
    }

    @Test
    void semConsumoOrientaAEnviarEPontosDeAtencaoOrdenamPorGravidade() {
        totals = new Totals(0, 0, 0, 0, 0);
        processes = List.of();
        tower = new TowerResponse(NOW, "danger", new Counts(1, 0, 1, 1, 1), List.of(
                new Item("pending-weekly-limit", "queue", "Limite semanal ainda provisório", "queued", "Ajuste."),
                new Item("database", "health", "Banco de dados Neon", "danger", "Sem conexão com o banco."),
                new Item("alert-projection-week", "queue", "Projeção estoura", "warn", "Ritmo alto")));
        refresh();
        ReportResponse r = report.report(NOW);
        assertThat(section(r, "Resumo executivo").paragraphs().get(0)).contains("CRÍTICO");
        assertThat(section(r, "Quem consome").paragraphs()).containsExactly("Nenhum consumo registrado no mês.");
        assertThat(section(r, "Quem consome").table()).isNull();
        List<String> att = section(r, "Pontos de atenção").bullets();
        assertThat(att.get(0)).startsWith("[crítico] Banco de dados Neon");
        assertThat(att.get(1)).startsWith("[atenção] Projeção estoura");
        assertThat(att.get(2)).startsWith("[na fila]");
        List<String> rec = section(r, "Recomendações").bullets();
        assertThat(rec).anyMatch(b -> b.contains("Sem consumo registrado no mês"));
        assertThat(rec).anyMatch(b -> b.contains("limite semanal"));
        assertThat(rec).anyMatch(b -> b.startsWith("Verifique o banco Neon"));
    }

    @Test
    void tudoEmOrdemTerminaSemAcaoNecessaria() {
        List<String> rec = section(report.report(NOW), "Recomendações").bullets();
        assertThat(rec).containsExactly("Nenhuma ação necessária no momento: consumo dentro do orçamento e processos saudáveis.");
        assertThat(section(report.report(NOW), "Pontos de atenção").paragraphs()).hasSize(1);
    }

    @Test
    void tabelaDeProcessosLimitadaAOitoLinhas() {
        List<Slice> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new Slice("p" + i, 100 - i, 1, 5.0));
        }
        processes = many;
        refresh();
        var table = section(report.report(NOW), "Quem consome").table();
        assertThat(table.headers()).containsExactly("Processo", "Tokens", "%", "Mensagens");
        assertThat(table.rows()).hasSize(8);
        assertThat(table.rows().get(0).get(0)).isEqualTo("p0");
    }

    @Test
    void ritmoNoTempo() {
        assertThat(ReportService.pace(50, 100, 50)).isEqualTo("alinhado ao ritmo linear");
        assertThat(ReportService.pace(80, 100, 50)).startsWith("ADIANTADO").contains("160%");
        assertThat(ReportService.pace(20, 100, 50)).startsWith("abaixo do ritmo linear");
        assertThat(ReportService.pace(0, 100, 0)).isEqualTo("período recém-iniciado");
    }
}
