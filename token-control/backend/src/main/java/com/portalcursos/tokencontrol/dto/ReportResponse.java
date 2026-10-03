package com.portalcursos.tokencontrol.dto;

import java.time.Instant;
import java.util.List;

/** Relatório de análise (pt-BR): seções com texto, marcadores e, opcionalmente, uma tabela. */
public record ReportResponse(Instant generatedAt, String title, String timezone, List<Section> sections) {

    public record Section(String title, List<String> paragraphs, List<String> bullets, Table table) {}

    public record Table(String caption, List<String> headers, List<List<String>> rows) {}
}
