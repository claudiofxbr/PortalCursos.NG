package com.portalcursos.tokencontrol.neon;

/** Sonda do banco (SQL). Interface para permitir teste do serviço sem Postgres. */
public interface DbProbe {
    /** Lança exceção se não conseguir consultar. */
    DbStatus probe(DbEndpointInfo endpoint, NeonApiInfo api);
}
