package com.portalcursos.tokencontrol.dto;

import java.time.Instant;
import java.util.List;

/**
 * Torre de Controle dos Processos: visão única e somente-leitura dos processos do app e dos que consomem tokens.
 * category: work (em andamento) | queue (fila/pendências) | health (saúde) | auto (processos automáticos/recorrentes).
 * status: ok | active | queued | warn | danger.
 */
public record TowerResponse(Instant generatedAt, String overall, Counts counts, List<Item> items) {

    public record Counts(int ok, int active, int queued, int warn, int danger) {}

    public record Item(String id, String category, String label, String status, String detail) {}
}
