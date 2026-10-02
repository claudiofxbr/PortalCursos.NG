package com.portalcursos.tokencontrol.repository;

import java.time.Instant;

public record HourUsage(Instant hour, long input, long output, long cacheCreation, long cacheRead, long messages) {

    public long counted(boolean countCacheReads) {
        return input + output + cacheCreation + (countCacheReads ? cacheRead : 0L);
    }
}
