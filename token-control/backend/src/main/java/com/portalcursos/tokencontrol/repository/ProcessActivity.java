package com.portalcursos.tokencontrol.repository;

import java.time.Instant;

/** Atividade de um processo (projeto/subagente) numa janela: somas e instante da última mensagem. */
public record ProcessActivity(String process, long input, long output, long cacheCreation, long cacheRead,
        long messages, Instant lastAt) {

    public long counted(boolean countCacheReads) {
        return input + output + cacheCreation + (countCacheReads ? cacheRead : 0L);
    }
}
