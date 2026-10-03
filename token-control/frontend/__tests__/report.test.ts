import { describe, expect, it } from "vitest";
import { reportFileName, toMarkdown } from "@/lib/report";
import type { Report } from "@/lib/types";

const report: Report = {
  generatedAt: "2026-10-03T02:30:00Z", // 23:30 de 02/10 em São Paulo
  title: "Relatório de análise — Controle de Tokens Claude Code",
  timezone: "America/Sao_Paulo",
  sections: [
    { title: "Resumo executivo", paragraphs: ["Estado geral da operação: operação normal."], bullets: [], table: null },
    { title: "Recomendações", paragraphs: [], bullets: ["Ajuste o limite semanal.", "Defina o orçamento mensal."], table: null },
    { title: "Quem consome (mês atual)", paragraphs: [], bullets: [],
      table: { caption: "Consumo por processo", headers: ["Processo", "Tokens"], rows: [["a|b", "1,2 mi"], ["token-control", "300 mil"]] } },
  ],
};

describe("relatório em Markdown", () => {
  it("gera título, seções, marcadores e tabela com | escapado", () => {
    const md = toMarkdown(report);
    expect(md).toContain("# Relatório de análise — Controle de Tokens Claude Code");
    expect(md).toContain("Gerado em");
    expect(md).toContain("## Resumo executivo\n\nEstado geral da operação: operação normal.");
    expect(md).toContain("- Ajuste o limite semanal.\n- Defina o orçamento mensal.");
    expect(md).toContain("**Consumo por processo**");
    expect(md).toContain("| Processo | Tokens |\n| --- | --- |\n| a\\|b | 1,2 mi |");
    expect(md.endsWith("\n")).toBe(true);
  });

  it("nome do arquivo usa a data no fuso configurado (não em UTC)", () => {
    expect(reportFileName(report)).toBe("relatorio-controle-de-tokens-2026-10-02.md");
    expect(reportFileName({ ...report, timezone: "UTC" })).toBe("relatorio-controle-de-tokens-2026-10-03.md");
  });
});
