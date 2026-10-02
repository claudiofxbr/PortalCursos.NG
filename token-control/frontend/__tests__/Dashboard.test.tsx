import { render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Dashboard from "@/components/Dashboard";
import type { HistoryEntry, Summary } from "@/lib/types";

// Recharts depende de layout real (ResponsiveContainer = 0px no jsdom); aqui validamos dados/estado, não SVG.
vi.mock("recharts", async () => {
  const React = await import("react");
  const Stub = ({ children }: { children?: React.ReactNode }) => React.createElement("div", null, children);
  const names = ["Area", "Bar", "BarChart", "CartesianGrid", "Cell", "ComposedChart", "Legend", "Line", "Pie", "PieChart",
    "ReferenceLine", "ResponsiveContainer", "Tooltip", "XAxis", "YAxis"];
  return Object.fromEntries(names.map((n) => [n, Stub]));
});

const summary: Summary = {
  config: { planName: "Claude Code Pro", resetDayOfWeek: 1, resetTime: "09:00", timezone: "America/Sao_Paulo", weeklyLimitTokens: 1_000_000, countCacheReads: false },
  cycle: { start: "2026-09-28T12:00:00Z", end: "2026-10-05T12:00:00Z", secondsRemaining: 273_600, elapsedPct: 45 },
  limit: 1_000_000, used: 720_000, remaining: 280_000, usedPct: 72,
  totals: { input: 100, output: 200, cacheCreation: 300, cacheRead: 400, messages: 42 },
  projection: { projectedTotal: 1_600_000, projectedPct: 160, dailyAverage: 228_000, willExceed: true, exhaustionAt: "2026-10-03T12:00:00Z" },
  last5hTokens: 55_000,
  daily: Array.from({ length: 7 }, (_, i) => ({ index: i + 1, date: `2026-09-${28 + i > 30 ? "30" : 28 + i}`, tokens: 1000 * i, cumulative: 1000 * i })),
  byProcess: [{ name: "PortalCursos.NG", tokens: 600_000, messages: 30, pct: 83.3 }, { name: "token-control", tokens: 120_000, messages: 12, pct: 16.7 }],
  byModel: [{ name: "claude-sonnet-5-5", tokens: 720_000, messages: 42, pct: 100 }],
};
const history: HistoryEntry[] = [{ start: "2026-09-28T12:00:00Z", end: "2026-10-05T12:00:00Z", used: 720_000, limit: 1_000_000, usedPct: 72, current: true }];

function mockFetch(responses: Record<string, unknown>, status = 200) {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    const key = Object.keys(responses).find((k) => String(url).includes(k));
    return new Response(JSON.stringify(key ? responses[key] : { error: "x" }), { status: key ? status : 404 });
  }));
}

describe("Dashboard", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("mostra uso, tempo até o reset, projeção e detalhe por processo", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history });
    render(<Dashboard />);
    await screen.findByText(/Uso do ciclo atual/);
    expect(screen.getByText("Atenção")).toBeInTheDocument(); // 72% → warn
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "72");
    expect(screen.getByText(/720 mil de 1 mi/)).toBeInTheDocument();
    expect(screen.getByText("3d 4h")).toBeInTheDocument();
    expect(screen.getByText(/esgota em/)).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Detalhe por processo" });
    expect(within(table).getByText("PortalCursos.NG")).toBeInTheDocument();
    expect(within(table).getByText("token-control")).toBeInTheDocument();
  });

  it("sem consumo no ciclo orienta a rodar o collector", async () => {
    mockFetch({
      "tokens/summary": { ...summary, used: 0, usedPct: 0, projection: null, totals: { ...summary.totals, messages: 0 }, byProcess: [], byModel: [] },
      "tokens/history": history,
    });
    render(<Dashboard />);
    await screen.findByText(/Nenhum consumo neste ciclo/);
    expect(screen.getByText("Dentro do ritmo")).toBeInTheDocument();
    expect(screen.getByText(/aguardando 1h de dados/)).toBeInTheDocument();
  });

  it("erro do backend aparece como alerta, sem quebrar", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "backend_unavailable" }), { status: 502 })));
    render(<Dashboard />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("backend_unavailable"));
  });
});
