package com.portalcursos.tokencontrol.service;

import com.portalcursos.tokencontrol.dto.ConfigDto;
import com.portalcursos.tokencontrol.dto.HistoryEntry;
import com.portalcursos.tokencontrol.dto.MonthSummaryResponse;
import com.portalcursos.tokencontrol.dto.SummaryResponse;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Cycle;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Day;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Projection;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Slice;
import com.portalcursos.tokencontrol.dto.SummaryResponse.Totals;
import com.portalcursos.tokencontrol.model.PlanConfig;
import com.portalcursos.tokencontrol.repository.HourUsage;
import com.portalcursos.tokencontrol.repository.UsageAggregate;
import com.portalcursos.tokencontrol.repository.UsageRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {

    private static final int TOP_SLICES = 12;
    private static final int DAYS = 7;

    private final UsageRepository repository;
    private final ConfigService configService;

    public SummaryService(UsageRepository repository, ConfigService configService) {
        this.repository = repository;
        this.configService = configService;
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Instant now) {
        PlanConfig config = configService.current();
        boolean cache = config.isCountCacheReads();
        CycleWindow window = CycleCalculator.cycleAt(config, now);

        UsageAggregate total = repository.total(window.start(), window.end());
        long used = total.counted(cache);
        long limit = config.getWeeklyLimitTokens();

        double elapsedPct = pct(Duration.between(window.start(), now).getSeconds(),
                Duration.between(window.start(), window.end()).getSeconds());
        Cycle cycle = new Cycle(window.start(), window.end(),
                Math.max(0, Duration.between(now, window.end()).getSeconds()), elapsedPct);

        long last5h = repository.total(now.minus(5, ChronoUnit.HOURS), now.plusSeconds(1)).counted(cache);

        return new SummaryResponse(ConfigService.toDto(config), cycle, limit, used, Math.max(0, limit - used),
                pct(used, limit),
                new Totals(total.input(), total.output(), total.cacheCreation(), total.cacheRead(), total.messages()),
                projection(used, limit, window, now), last5h,
                daily(config, window, repository.aggregateByHour(window.start(), window.end())),
                slices(repository.aggregateByProcess(window.start(), window.end()), cache),
                slices(repository.aggregateByModel(window.start(), window.end()), cache));
    }

    @Transactional(readOnly = true)
    public MonthSummaryResponse month(Instant now) {
        PlanConfig config = configService.current();
        boolean cache = config.isCountCacheReads();
        ZoneId zone = ZoneId.of(config.getTimezone());
        CycleWindow window = MonthCalculator.windowAt(zone, now);
        LocalDate firstDay = MonthCalculator.firstDay(zone, now);
        int daysInMonth = firstDay.lengthOfMonth();

        var totals = repository.total(window.start(), window.end());
        long used = totals.counted(cache);
        boolean estimated = config.getMonthlyLimitTokens() == null;
        long limit = estimated ? Math.round(config.getWeeklyLimitTokens() * daysInMonth / 7.0)
                : config.getMonthlyLimitTokens();

        double elapsedPct = pct(Duration.between(window.start(), now).getSeconds(),
                Duration.between(window.start(), window.end()).getSeconds());
        var period = new MonthSummaryResponse.Period(window.start(), window.end(), daysInMonth,
                now.atZone(zone).getDayOfMonth(), Math.max(0, Duration.between(now, window.end()).getSeconds()),
                elapsedPct);

        return new MonthSummaryResponse(ConfigService.toDto(config), period, limit, estimated, used,
                Math.max(0, limit - used), pct(used, limit),
                new Totals(totals.input(), totals.output(), totals.cacheCreation(), totals.cacheRead(),
                        totals.messages()),
                projection(used, limit, window, now),
                dailyOfMonth(zone, firstDay, daysInMonth, cache,
                        repository.aggregateByHour(window.start(), window.end())),
                slices(repository.aggregateByProcess(window.start(), window.end()), cache),
                slices(repository.aggregateByModel(window.start(), window.end()), cache));
    }

    /** Um item por dia civil do mês (dia 1..N), com acumulado. */
    static List<Day> dailyOfMonth(ZoneId zone, LocalDate firstDay, int daysInMonth, boolean cache,
            List<HourUsage> hours) {
        long[] perDay = new long[daysInMonth];
        for (HourUsage h : hours) {
            int idx = (int) ChronoUnit.DAYS.between(firstDay, h.hour().atZone(zone).toLocalDate());
            perDay[Math.min(daysInMonth - 1, Math.max(0, idx))] += h.counted(cache);
        }
        List<Day> out = new ArrayList<>();
        long cumulative = 0;
        for (int i = 0; i < daysInMonth; i++) {
            cumulative += perDay[i];
            out.add(new Day(i + 1, firstDay.plusDays(i), perDay[i], cumulative));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Instant now, int cycles) {
        PlanConfig config = configService.current();
        long limit = config.getWeeklyLimitTokens();
        CycleWindow window = CycleCalculator.cycleAt(config, now);
        List<HistoryEntry> out = new ArrayList<>();
        for (int i = 0; i < cycles; i++) {
            long used = repository.total(window.start(), window.end()).counted(config.isCountCacheReads());
            out.add(0, new HistoryEntry(window.start(), window.end(), used, limit, pct(used, limit), i == 0));
            window = CycleCalculator.cycleAt(config, window.start().minusSeconds(1));
        }
        return out;
    }

    static Projection projection(long used, long limit, CycleWindow window, Instant now) {
        long elapsed = Duration.between(window.start(), now).getSeconds();
        long total = Duration.between(window.start(), window.end()).getSeconds();
        if (elapsed < 3600 || used <= 0) {
            return null; // base estatística insuficiente
        }
        double rate = (double) used / elapsed; // tokens por segundo no ciclo até agora
        long projected = Math.round(rate * total);
        long dailyAvg = Math.round(rate * 86_400);
        Instant exhaustion = null;
        if (used >= limit) {
            exhaustion = now;
        } else if (projected > limit) {
            exhaustion = now.plusSeconds((long) ((limit - used) / rate));
        }
        return new Projection(projected, pct(projected, limit), dailyAvg, projected > limit, exhaustion);
    }

    /** Dias do ciclo (alinhados ao horário de reset): dia 1 começa no reset, dia 7 termina no próximo. */
    static List<Day> daily(PlanConfig config, CycleWindow window, List<HourUsage> hours) {
        ZoneId zone = ZoneId.of(config.getTimezone());
        LocalDate startDate = CycleCalculator.startDate(config, window);
        long[] perDay = new long[DAYS];
        for (HourUsage h : hours) {
            ZonedDateTime local = h.hour().atZone(zone);
            LocalDate date = local.toLocalDate();
            if (local.toLocalTime().isBefore(config.getResetTime())) {
                date = date.minusDays(1);
            }
            int idx = (int) ChronoUnit.DAYS.between(startDate, date);
            perDay[Math.min(DAYS - 1, Math.max(0, idx))] += h.counted(config.isCountCacheReads());
        }
        List<Day> out = new ArrayList<>();
        long cumulative = 0;
        for (int i = 0; i < DAYS; i++) {
            cumulative += perDay[i];
            out.add(new Day(i + 1, startDate.plusDays(i), perDay[i], cumulative));
        }
        return out;
    }

    /** Top N por tokens; o restante vira "Outros" para o gráfico não explodir. */
    static List<Slice> slices(List<UsageAggregate> rows, boolean cache) {
        List<UsageAggregate> sorted = rows.stream()
                .filter(r -> r.counted(cache) > 0 || r.messages() > 0)
                .sorted(Comparator.comparingLong((UsageAggregate r) -> r.counted(cache)).reversed()).toList();
        long sum = sorted.stream().mapToLong(r -> r.counted(cache)).sum();
        List<Slice> out = new ArrayList<>();
        long otherTokens = 0, otherMessages = 0;
        for (int i = 0; i < sorted.size(); i++) {
            UsageAggregate r = sorted.get(i);
            if (i < TOP_SLICES) {
                out.add(new Slice(r.key(), r.counted(cache), r.messages(), pct(r.counted(cache), sum)));
            } else {
                otherTokens += r.counted(cache);
                otherMessages += r.messages();
            }
        }
        if (otherMessages > 0) {
            out.add(new Slice("Outros", otherTokens, otherMessages, pct(otherTokens, sum)));
        }
        return out;
    }

    private static double pct(long part, long whole) {
        if (whole <= 0) {
            return 0;
        }
        return Math.round(Math.max(part, 0) * 10_000.0 / whole) / 100.0;
    }
}
