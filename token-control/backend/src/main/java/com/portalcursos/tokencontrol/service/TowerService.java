package com.portalcursos.tokencontrol.service;

import com.portalcursos.tokencontrol.dto.MonthSummaryResponse;
import com.portalcursos.tokencontrol.dto.SummaryResponse;
import com.portalcursos.tokencontrol.dto.TowerResponse;
import com.portalcursos.tokencontrol.dto.TowerResponse.Counts;
import com.portalcursos.tokencontrol.dto.TowerResponse.Item;
import com.portalcursos.tokencontrol.model.PlanConfig;
import com.portalcursos.tokencontrol.neon.DbStatus;
import com.portalcursos.tokencontrol.neon.DbStatusService;
import com.portalcursos.tokencontrol.neon.NeonApiInfo;
import com.portalcursos.tokencontrol.repository.ProcessActivity;
import com.portalcursos.tokencontrol.repository.UsageRepository;
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
 * Torre de Controle dos Processos — calcula, sob demanda e sem tabelas novas, o estado de cada processo do app
 * (coleta, banco, API, ciclo semanal, mês) e quem está consumindo tokens agora. Somente leitura.
 * Limiares de severidade (70% atenção, 90% crítico) são os mesmos do dashboard.
 */
@Service
public class TowerService {

    static final long COLLECTOR_OK_HOURS = 6;
    static final long COLLECTOR_WARN_HOURS = 48;
    static final long DEFAULT_WEEKLY_LIMIT = 50_000_000L; // valor provisório semeado pela migration V1
    private static final int MAX_ACTIVE_PROCESSES = 8;

    private final SummaryService summaryService;
    private final ConfigService configService;
    private final DbStatusService dbStatusService;
    private final UsageRepository repository;

    public TowerService(SummaryService summaryService, ConfigService configService, DbStatusService dbStatusService,
            UsageRepository repository) {
        this.summaryService = summaryService;
        this.configService = configService;
        this.dbStatusService = dbStatusService;
        this.repository = repository;
    }

    public TowerResponse tower(Instant now) {
        PlanConfig config = configService.current();
        ZoneId zone = ZoneId.of(config.getTimezone());
        SummaryResponse week = summaryService.summary(now);
        MonthSummaryResponse month = summaryService.month(now);
        DbStatus db = dbStatusService.status();

        List<Item> items = new ArrayList<>();
        activeProcesses(items, now, zone, config.isCountCacheReads());
        alerts(items, week, month, zone);
        pendencies(items, config, db);
        health(items, db);
        automatic(items, now, week, month, zone);

        Counts counts = count(items);
        String overall = counts.danger() > 0 ? "danger" : counts.warn() > 0 ? "warn" : "ok";
        return new TowerResponse(now, overall, counts, List.copyOf(items));
    }

    // ---- em andamento: processos que consumiram tokens nas últimas 5 horas
    private void activeProcesses(List<Item> out, Instant now, ZoneId zone, boolean cache) {
        List<ProcessActivity> live = repository.activityByProcess(now.minus(Duration.ofHours(5)), now.plusSeconds(1));
        live.stream().sorted(Comparator.comparingLong((ProcessActivity a) -> a.counted(cache)).reversed())
                .limit(MAX_ACTIVE_PROCESSES)
                .forEach(a -> out.add(new Item("work-" + a.process(), "work", a.process(), "active",
                        tokens(a.counted(cache)) + " tokens e " + a.messages() + " mensagens nas últimas 5 h · última atividade há "
                                + human(Duration.between(a.lastAt(), now)))));
    }

    // ---- fila/pendências: alertas de projeção e itens de configuração que ainda dependem do usuário
    private void alerts(List<Item> out, SummaryResponse week, MonthSummaryResponse month, ZoneId zone) {
        if (week.projection() != null && week.projection().willExceed()) {
            out.add(new Item("alert-projection-week", "queue", "Projeção estoura o orçamento semanal",
                    week.usedPct() >= 100 ? "danger" : "warn", "Ritmo atual projeta " + tokens(week.projection().projectedTotal())
                            + " (" + pct(week.projection().projectedPct()) + " do orçamento)" + exhaustion(week.projection().exhaustionAt(), zone)));
        }
        if (month.projection() != null && month.projection().willExceed()) {
            out.add(new Item("alert-projection-month", "queue", "Projeção estoura o orçamento do mês",
                    month.usedPct() >= 100 ? "danger" : "warn", "Ritmo atual projeta " + tokens(month.projection().projectedTotal())
                            + " (" + pct(month.projection().projectedPct()) + " do orçamento)" + exhaustion(month.projection().exhaustionAt(), zone)));
        }
    }

    private void pendencies(List<Item> out, PlanConfig config, DbStatus db) {
        if (config.getWeeklyLimitTokens() == DEFAULT_WEEKLY_LIMIT) {
            out.add(new Item("pending-weekly-limit", "queue", "Limite semanal ainda provisório", "queued",
                    "Está no valor inicial (" + tokens(DEFAULT_WEEKLY_LIMIT) + "). Ajuste em Configurar plano conforme o /usage do Claude Code."));
        }
        if (config.getMonthlyLimitTokens() == null) {
            out.add(new Item("pending-monthly-limit", "queue", "Orçamento mensal não definido", "queued",
                    "O mês usa referência estimada (semanal × dias do mês ÷ 7). Defina um valor em Configurar plano."));
        }
        NeonApiInfo api = db.neonApi();
        if (api == null || !api.enabled()) {
            out.add(new Item("pending-neon-api", "queue", "API do Neon desligada", "queued",
                    "Defina NEON_API_KEY e NEON_PROJECT_ID para ver consumo, branches e computes do projeto."));
        } else if (!api.ok()) {
            out.add(new Item("pending-neon-api", "queue", "API do Neon com erro", "warn",
                    "Falha ao consultar a API de gestão do Neon: " + api.error() + ". Confira NEON_API_KEY e o NEON_PROJECT_ID (ID do projeto, não de branch)."));
        }
    }

    // ---- saúde do sistema
    private void health(List<Item> out, DbStatus db) {
        out.add(new Item("api", "health", "API do Controle de Tokens", "ok", "Respondendo (checagem feita agora)."));
        String status;
        String detail;
        if (!db.connected()) {
            status = "danger";
            detail = "Sem conexão com o banco.";
        } else {
            boolean failed = "FAILED".equals(db.migrations().status());
            status = failed ? "danger" : db.latencyMs() > 1000 ? "warn" : "ok";
            detail = "Conectado · latência " + db.latencyMs() + " ms · migrations " + db.migrations().status()
                    + (db.migrations().latest() != null ? " v" + db.migrations().latest() : "")
                    + (db.endpoint().neon() ? " · " + (db.endpoint().pooled() ? "pooled" : "conexão direta") : "");
        }
        out.add(new Item("database", "health", "Banco de dados Neon (Postgres)", status, detail));
    }

    // ---- processos automáticos/recorrentes (sempre visíveis)
    private void automatic(List<Item> out, Instant now, SummaryResponse week, MonthSummaryResponse month, ZoneId zone) {
        out.add(collector(now));
        out.add(new Item("cycle-week", "auto", "Ciclo semanal (" + dayLabel(week.config().resetDayOfWeek()) + " " + week.config().resetTime() + ")",
                severity(week.usedPct()), tokens(week.used()) + " de " + tokens(week.limit()) + " (" + pct(week.usedPct())
                        + ") · reset em " + human(Duration.ofSeconds(week.cycle().secondsRemaining()))));
        out.add(new Item("cycle-month", "auto", "Mês atual", severity(month.usedPct()),
                tokens(month.used()) + " de " + tokens(month.limit()) + (month.limitEstimated() ? " (estimado)" : "") + " ("
                        + pct(month.usedPct()) + ") · fim do mês em " + human(Duration.ofSeconds(month.period().secondsRemaining()))));
    }

    private Item collector(Instant now) {
        Instant lastIngest = repository.lastIngestedAt();
        if (lastIngest == null) {
            return new Item("collector", "auto", "Coletor de consumo (Claude Code)", "queued",
                    "Nenhum envio recebido ainda. Rode o enviar-consumo.ps1 no computador onde o Claude Code roda.");
        }
        Duration age = Duration.between(lastIngest, now);
        String status = age.toHours() < COLLECTOR_OK_HOURS ? "ok" : age.toHours() < COLLECTOR_WARN_HOURS ? "warn" : "danger";
        Instant lastUse = repository.lastOccurredAt();
        String detail = "Último envio há " + human(age) + (lastUse != null ? " · última atividade registrada há "
                + human(Duration.between(lastUse, now)) : "");
        if (!"ok".equals(status)) {
            detail += " · o painel pode estar desatualizado; rode o enviar-consumo.ps1.";
        }
        return new Item("collector", "auto", "Coletor de consumo (Claude Code)", status, detail);
    }

    // ---- helpers
    static Counts count(List<Item> items) {
        int ok = 0, active = 0, queued = 0, warn = 0, danger = 0;
        for (Item i : items) {
            switch (i.status()) {
                case "ok" -> ok++;
                case "active" -> active++;
                case "queued" -> queued++;
                case "warn" -> warn++;
                default -> danger++;
            }
        }
        return new Counts(ok, active, queued, warn, danger);
    }

    public static String severity(double usedPct) {
        return usedPct >= 90 ? "danger" : usedPct >= 70 ? "warn" : "ok";
    }

    private static String exhaustion(Instant at, ZoneId zone) {
        return at == null ? "" : " · esgota em " + DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.of("pt", "BR")).withZone(zone).format(at);
    }

    private static String dayLabel(int isoDay) {
        return new String[] {"", "seg", "ter", "qua", "qui", "sex", "sáb", "dom"}[isoDay];
    }

    public static String human(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long days = s / 86_400, hours = (s % 86_400) / 3_600, minutes = (s % 3_600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "min";
        }
        return minutes > 0 ? minutes + "min" : "menos de 1 min";
    }

    public static String tokens(long n) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
        nf.setMaximumFractionDigits(1);
        double a = Math.abs((double) n);
        if (a >= 1_000_000_000) return nf.format(n / 1_000_000_000.0) + " bi";
        if (a >= 1_000_000) return nf.format(n / 1_000_000.0) + " mi";
        if (a >= 10_000) return nf.format(n / 1_000.0) + " mil";
        return NumberFormat.getIntegerInstance(Locale.of("pt", "BR")).format(n);
    }

    private static String pct(double p) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
        nf.setMaximumFractionDigits(1);
        return nf.format(p) + "%";
    }
}
