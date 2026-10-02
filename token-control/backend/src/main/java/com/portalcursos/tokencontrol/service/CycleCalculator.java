package com.portalcursos.tokencontrol.service;

import com.portalcursos.tokencontrol.model.PlanConfig;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/** Cálculo puro do ciclo semanal — aritmética em data local, portanto segura contra DST. */
public final class CycleCalculator {

    private CycleCalculator() {}

    public static CycleWindow cycleAt(PlanConfig config, Instant now) {
        ZoneId zone = ZoneId.of(config.getTimezone());
        DayOfWeek day = DayOfWeek.of(config.getResetDayOfWeek());
        ZonedDateTime local = now.atZone(zone);

        LocalDate date = local.toLocalDate().with(TemporalAdjusters.previousOrSame(day));
        Instant start = atReset(date, config, zone);
        if (start.isAfter(now)) { // hoje é o dia do reset, mas o horário ainda não chegou
            date = date.minusWeeks(1);
            start = atReset(date, config, zone);
        }
        Instant end = atReset(date.plusWeeks(1), config, zone);
        return new CycleWindow(start, end);
    }

    /** Data local do primeiro dia do ciclo (para rotular os dias 1..7). */
    public static LocalDate startDate(PlanConfig config, CycleWindow window) {
        return window.start().atZone(ZoneId.of(config.getTimezone())).toLocalDate();
    }

    private static Instant atReset(LocalDate date, PlanConfig config, ZoneId zone) {
        // ZonedDateTime.of resolve lacunas de DST avançando para o próximo horário válido
        return ZonedDateTime.of(LocalDateTime.of(date, config.getResetTime()), zone).toInstant();
    }
}
