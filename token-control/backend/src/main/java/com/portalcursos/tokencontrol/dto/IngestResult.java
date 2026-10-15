package com.portalcursos.tokencontrol.dto;

/** {@code duplicates} = já existiam; {@code updated} = dentre elas, as corrigidas por terem chegado completas (output maior). */
public record IngestResult(int received, int inserted, int duplicates, int updated) {}
