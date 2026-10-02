package com.portalcursos.tokencontrol.repository;

/** Somas por grupo; quem consome decide se cache_read entra na conta (config do plano). */
public record UsageAggregate(String key, long input, long output, long cacheCreation, long cacheRead, long messages) {

    public long counted(boolean countCacheReads) {
        return input + output + cacheCreation + (countCacheReads ? cacheRead : 0L);
    }
}
