package com.portalcursos.tokencontrol.service;

import static com.portalcursos.tokencontrol.service.TowerService.human;
import static com.portalcursos.tokencontrol.service.TowerService.tokens;

import com.portalcursos.tokencontrol.dto.MonthSummaryResponse;
import com.portalcursos.tokencontrol.dto.ReportResponse;
import com.portalcursos.tokencontrol.dto.ReportResponse.Section;
import com.portalcursos.tokencontrol.dto.ReportResponse.Table;
import com.portalcursos.tokencontrol.dto.SummaryResponse;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Day;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Projection;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Slice;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Totals;
import com.portalcursos.tokencontrol.dto.TowerResponse;
import com.portalcursos.tokencontrol.dto.TowerResponse.Item;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Relatório de análise do aplicativo (uso e operação), em português: resumo executivo, semana, mês,
 * quem consome, pontos de atenção e recomendações acionáveis. Somente leitura, calculado sob demanda
 * a partir dos mesmos serviços do dashboard e da Torre (números sempre consistentes entre as telas).
 */
@Service
public class ReportService {

    static final double CONCENTRATION_THRESHOLD = 50.0;
    static final double CACHE_READ_SHARE_THRESHOLD = 80.0;
    private static final int TOP_PROCESSES = 8;
    private static final int TOP_MODELS = 5;

    private final SummaryService summaryService;
    private final TowerService towerService;

    public ReportService(SummaryService summaryService, TowerService towerService) {
        this.summaryService = summaryService;
        this.towerService = towerService;
    }

    public ReportResponse report(Instant now) {
        SummaryResponse week = summaryService.summary(now);
        MonthSummaryResponse month = summaryService.month(now);
        TowerResponse tower = towerService.tower(now);
        ZoneId zone = ZoneId.of(week.config().timezone());

        List<Section> sections = new ArrayList<>();
        sections.add(executive(week, month, tower, zone, now));
        sections.add(period("Ciclo semanal", week.used(), week.limit(), week.usedPct(), week.projection(), week.daily(),
                week.cycle().secondsRemaining(), week.cycle().elapsedPct(), week.remaining(), "reset", zone,
                " · últimas 5 horas: " + tokens(week.last5hTokens())));
        sections.add(period("Mês atual", month.used(), month.limit(), month.usedPct(), month.projection(), month.daily(),
                month.period().secondsRemaining(), month.period().elapsedPct(), month.remaining(), "fim do mês", zone,
                month.limitEstimated() ? " · limite estimado (semanal × dias ÷ 7)" : ""));
        sections.add(consumers(month));
        sections.add(attention(tower));
        sections.add(recommendations(week, month, tower));

        return new ReportResponse(now, "Relatório de análise — Controle de Tokens Claude Code", week.config().timezone(),
                List.copyOf(sections));
    }

    private Section executive(SummaryResponse week, MonthSummaryResponse month, TowerResponse tower, ZoneId zone, Instant now) {
        String overall = switch (tower.overall()) {
            case "danger" -> "CRÍTICO";
            case "warn" -> "ATENÇÃO";
            default -> "operação normal";
        };
        List<String> p = new ArrayList<>();
        p.add("Estado geral da operação: " + overall + " (" + tower.counts().danger() + " crítico(s), " + tower.counts().warn()
                + " em atenção, " + tower.counts().queued() + " pendência(s) na fila).");
        p.add("Ciclo semanal: usou " + tokens(week.used()) + " de " + tokens(week.limit()) + " (" + pct(week.usedPct())
                + "); restam " + tokens(week.remaining()) + "; reset em " + human(Duration.ofSeconds(week.cycle().secondsRemaining())) + ".");
        p.add("Mês atual: usou " + tokens(month.used()) + " de " + tokens(month.limit()) + (month.limitEstimated() ? " (estimado)" : "")
                + " (" + pct(month.usedPct()) + "); restam " + tokens(month.remaining()) + "; fim do mês em "
                + human(Duration.ofSeconds(month.period().secondsRemaining())) + ".");
        p.add("Dados analisados: " + number(month.totals().messages()) + " mensagem(ns) no mês. "
                + tower.items().stream().filter(i -> i.id().equals("collector")).map(Item::detail).findFirst().orElse(""));
        p.add("Relatório gerado em " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("pt", "BR")).withZone(zone).format(now)
                + " (" + zone + ").");
        return new Section("Resumo executivo", p, List.of(), null);
    }

    private Section period(String title, long used, long limit, double usedPct, Projection projection, List<Day> daily,
            long secondsRemaining, double elapsedPct, long remaining, String endLabel, ZoneId zone, String extra) {
        List<String> bullets = new ArrayList<>();
        bullets.add("Uso: " + tokens(used) + " de " + tokens(limit) + " (" + pct(usedPct) + ") · " + endLabel + " em "
                + human(Duration.ofSeconds(secondsRemaining)) + extra);
        bullets.add("Ritmo no tempo: " + pace(used, limit, elapsedPct) + " (" + pct(elapsedPct) + " do período decorrido).");
        if (projection == null) {
            bullets.add("Projeção: sem base estatística ainda (menos de 1 h de período ou nenhum consumo).");
        } else {
            bullets.add("Projeção até o " + endLabel + ": " + tokens(projection.projectedTotal()) + " (" + pct(projection.projectedPct())
                    + " do orçamento) · média de " + tokens(projection.dailyAverage()) + " por dia"
                    + (projection.willExceed() ? " · VAI ESTOURAR o orçamento" + (projection.exhaustionAt() != null
                            ? " por volta de " + DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(zone).format(projection.exhaustionAt()) : "")
                            : " · dentro do orçamento"));
        }
        double daysLeft = secondsRemaining / 86_400.0;
        if (daysLeft > 0) {
            bullets.add("Ritmo máximo para fechar dentro do orçamento: " + tokens(Math.round(remaining / daysLeft)) + " por dia nos "
                    + number(Math.round(daysLeft * 10) / 10.0) + " dia(s) restantes.");
        }
        daily.stream().filter(d -> d.tokens() > 0).max(Comparator.comparingLong(Day::tokens)).ifPresent(d -> bullets.add(
                "Dia mais intenso: " + String.format("%02d/%02d", d.date().getDayOfMonth(), d.date().getMonthValue()) + " com "
                        + tokens(d.tokens()) + "."));
        return new Section(title, List.of(), bullets, null);
    }

    private Section consumers(MonthSummaryResponse month) {
        List<List<String>> procRows = new ArrayList<>();
        month.byProcess().stream().limit(TOP_PROCESSES).forEach(s -> procRows.add(List.of(s.name(), tokens(s.tokens()), pct(s.pct()),
                number(s.messages()))));
        List<String> bullets = new ArrayList<>();
        bullets.add("Por modelo: " + (month.byModel().isEmpty() ? "sem dados."
                : String.join("; ", month.byModel().stream().limit(TOP_MODELS).map(m -> m.name() + " " + tokens(m.tokens()) + " (" + pct(m.pct()) + ")").toList()) + "."));
        Totals t = month.totals();
        double raw = (double) t.input() + t.output() + t.cacheCreation() + t.cacheRead();
        if (raw > 0) {
            bullets.add("Composição bruta dos tokens no mês: entrada " + pct(t.input() * 100 / raw) + ", saída " + pct(t.output() * 100 / raw)
                    + ", criação de cache " + pct(t.cacheCreation() * 100 / raw) + ", leitura de cache " + pct(t.cacheRead() * 100 / raw) + ".");
        }
        Table table = procRows.isEmpty() ? null
                : new Table("Consumo por processo no mês (top " + TOP_PROCESSES + ")", List.of("Processo", "Tokens", "%", "Mensagens"), procRows);
        return new Section("Quem consome (mês atual)", month.byProcess().isEmpty() ? List.of("Nenhum consumo registrado no mês.") : List.of(), bullets, table);
    }

    private Section attention(TowerResponse tower) {
        List<String> bullets = tower.items().stream().filter(i -> List.of("danger", "warn", "queued").contains(i.status()))
                .sorted(Comparator.comparingInt((Item i) -> rank(i.status())))
                .map(i -> "[" + statusLabel(i.status()) + "] " + i.label() + " — " + i.detail()).toList();
        return new Section("Pontos de atenção (Torre de Controle)",
                bullets.isEmpty() ? List.of("Nenhum ponto de atenção: todos os processos estão dentro do esperado.") : List.of(), bullets, null);
    }

    private Section recommendations(SummaryResponse week, MonthSummaryResponse month, TowerResponse tower) {
        List<String> r = new ArrayList<>();
        if (month.totals().messages() == 0) {
            r.add("Sem consumo registrado no mês: rode o enviar-consumo.ps1 no computador onde o Claude Code roda para alimentar o painel.");
        }
        projectionAdvice(r, "semanal", week.projection(), week.remaining(), week.cycle().secondsRemaining());
        projectionAdvice(r, "do mês", month.projection(), month.remaining(), month.period().secondsRemaining());
        if (!month.byProcess().isEmpty() && month.byProcess().get(0).pct() >= CONCENTRATION_THRESHOLD && month.byProcess().size() > 1) {
            Slice top = month.byProcess().get(0);
            r.add("O processo \"" + top.name() + "\" concentra " + pct(top.pct()) + " do consumo do mês: revise sessões longas e o tamanho do contexto nesse projeto.");
        }
        Totals t = month.totals();
        double raw = (double) t.input() + t.output() + t.cacheCreation() + t.cacheRead();
        if (raw > 0 && t.cacheRead() * 100 / raw >= CACHE_READ_SHARE_THRESHOLD && !week.config().countCacheReads()) {
            r.add("Leituras de cache são " + pct(t.cacheRead() * 100 / raw) + " do volume bruto e estão fora da conta (padrão). Se o limite do seu plano considera esse volume, ative \"Contar leituras de cache\" em Configurar plano.");
        }
        for (Item i : tower.items()) {
            switch (i.id()) {
                case "collector" -> {
                    if (!"ok".equals(i.status()) && month.totals().messages() > 0) {
                        r.add("Atualize os dados: " + i.detail());
                    }
                }
                case "pending-weekly-limit" -> r.add("Ajuste o limite semanal para o valor real do seu plano (veja o /usage do Claude Code): hoje está no valor provisório.");
                case "pending-monthly-limit" -> r.add("Defina o orçamento mensal em Configurar plano; hoje o mês usa uma referência estimada.");
                case "pending-neon-api" -> r.add(i.detail());
                case "database" -> {
                    if ("danger".equals(i.status()) || "warn".equals(i.status())) {
                        r.add("Verifique o banco Neon: " + i.detail());
                    }
                }
                default -> { }
            }
        }
        if (r.isEmpty()) {
            r.add("Nenhuma ação necessária no momento: consumo dentro do orçamento e processos saudáveis.");
        }
        return new Section("Recomendações", List.of(), List.copyOf(r), null);
    }

    private static void projectionAdvice(List<String> out, String label, Projection p, long remaining, long secondsRemaining) {
        if (p == null) {
            return;
        }
        double daysLeft = secondsRemaining / 86_400.0;
        if (p.willExceed() && daysLeft > 0) {
            long allowed = Math.round(remaining / daysLeft);
            out.add("Ritmo " + label + " acima do orçamento: a média atual é " + tokens(p.dailyAverage()) + " por dia e o máximo para fechar dentro do limite é "
                    + tokens(allowed) + " por dia. Reduza o consumo ou revise o limite configurado.");
        }
    }

    public static String pace(long used, long limit, double elapsedPct) {
        double ideal = limit * elapsedPct / 100.0;
        if (ideal <= 0) {
            return "período recém-iniciado";
        }
        double ratio = used / ideal;
        return ratio > 1.1 ? "ADIANTADO em relação ao ritmo linear (" + pct(ratio * 100) + " do esperado até agora)"
                : ratio < 0.9 ? "abaixo do ritmo linear (" + pct(ratio * 100) + " do esperado até agora)"
                : "alinhado ao ritmo linear";
    }

    private static int rank(String status) {
        return switch (status) {
            case "danger" -> 0;
            case "warn" -> 1;
            default -> 2;
        };
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "danger" -> "crítico";
            case "warn" -> "atenção";
            default -> "na fila";
        };
    }

    private static String number(double n) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
        nf.setMaximumFractionDigits(1);
        return nf.format(n);
    }

    private static String pct(double p) {
        return number(p) + "%";
    }
}
