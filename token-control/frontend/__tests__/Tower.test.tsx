import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import TowerPanel, { overallSummary } from "@/components/TowerPanel";
import type { Tower } from "@/lib/types";

const base: Tower = {
  generatedAt: "2026-10-02T18:00:00Z",
  overall: "warn",
  counts: { ok: 3, active: 1, queued: 2, warn: 1, danger: 0 },
  items: [
    { id: "work-token-control", category: "work", label: "token-control", status: "active", detail: "12 mil tokens e 30 mensagens nas últimas 5 h" },
    { id: "pending-weekly-limit", category: "queue", label: "Limite semanal ainda provisório", status: "queued", detail: "Ajuste em Configurar plano." },
    { id: "alert-projection-week", category: "queue", label: "Projeção estoura o orçamento semanal", status: "warn", detail: "Ritmo atual projeta 120 mi" },
    { id: "api", category: "health", label: "API do Controle de Tokens", status: "ok", detail: "Respondendo" },
    { id: "database", category: "health", label: "Banco de dados Neon (Postgres)", status: "ok", detail: "Conectado" },
    { id: "collector", category: "auto", label: "Coletor de consumo (Claude Code)", status: "ok", detail: "Último envio há 1h 0min" },
  ],
};

describe("TowerPanel", () => {
  it("mostra o estado geral e agrupa os processos nas quatro seções, com status em texto", () => {
    render(<TowerPanel tower={base} timeZone="America/Sao_Paulo" />);
    const overall = screen.getByRole("region", { name: "Estado geral" });
    expect(within(overall).getByText("Torre de Controle dos Processos")).toBeInTheDocument();
    expect(within(overall).getByText("Atenção")).toBeInTheDocument();
    expect(within(overall).getByText(/1 item\(ns\) pedem acompanhamento/)).toBeInTheDocument();

    const work = screen.getByRole("region", { name: "Em andamento" });
    expect(within(work).getByText("token-control")).toBeInTheDocument();
    expect(within(work).getByText(/ativo/)).toBeInTheDocument();

    const queue = screen.getByRole("region", { name: "Fila / pendências" });
    expect(within(queue).getByText("Limite semanal ainda provisório")).toBeInTheDocument();
    expect(within(queue).getByText(/na fila/)).toBeInTheDocument();
    expect(within(queue).getByText(/atenção/)).toBeInTheDocument();

    expect(within(screen.getByRole("region", { name: "Saúde do sistema" })).getByText("Banco de dados Neon (Postgres)")).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Processos automáticos recorrentes" })).getByText("Coletor de consumo (Claude Code)")).toBeInTheDocument();
  });

  it("sem processo em andamento orienta; sem pendência diz que não há", () => {
    render(<TowerPanel tower={{ ...base, items: base.items.filter((i) => i.category === "health" || i.category === "auto") }} timeZone="UTC" />);
    expect(screen.getByText("Nenhum processo consumiu tokens nas últimas 5 horas.")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma pendência.")).toBeInTheDocument();
  });

  it("resumo textual por severidade", () => {
    expect(overallSummary({ ...base, overall: "danger", counts: { ...base.counts, danger: 2, warn: 1 } })).toBe("2 item(ns) crítico(s) e 1 em atenção.");
    expect(overallSummary({ ...base, overall: "ok", counts: { ...base.counts, warn: 0, queued: 2 } })).toBe("Tudo dentro do esperado · 2 pendência(s) na fila.");
  });
});
