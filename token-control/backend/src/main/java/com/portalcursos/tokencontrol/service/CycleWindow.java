package com.portalcursos.tokencontrol.service;

import java.time.Instant;

/** Ciclo semanal [start, end): do último reset (inclusive) ao próximo reset (exclusive). */
public record CycleWindow(Instant start, Instant end) {

    public CycleWindow previous() {
        long span = end.getEpochSecond() - start.getEpochSecond();
        return new CycleWindow(start.minusSeconds(span), start);
    }
}
