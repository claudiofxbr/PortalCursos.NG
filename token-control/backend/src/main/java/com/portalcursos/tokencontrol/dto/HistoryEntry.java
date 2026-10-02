package com.portalcursos.tokencontrol.dto;

import java.time.Instant;

public record HistoryEntry(Instant start, Instant end, long used, long limit, double usedPct, boolean current) {}
