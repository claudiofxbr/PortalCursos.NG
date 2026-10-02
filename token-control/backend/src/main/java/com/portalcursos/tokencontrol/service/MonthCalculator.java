package com.portalcursos.tokencontrol.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Mês civil no fuso configurado: [dia 1 00:00, dia 1 do mês seguinte 00:00). Aritmética em data local (seguro contra DST). */
public final class MonthCalculator {

    private MonthCalculator() {}

    public static LocalDate firstDay(ZoneId zone, Instant now) {
        return now.atZone(zone).toLocalDate().withDayOfMonth(1);
    }

    public static CycleWindow windowAt(ZoneId zone, Instant now) {
        LocalDate first = firstDay(zone, now);
        return new CycleWindow(first.atStartOfDay(zone).toInstant(), first.plusMonths(1).atStartOfDay(zone).toInstant());
    }
}
