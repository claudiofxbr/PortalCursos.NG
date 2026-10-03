import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Dashboard from "@/components/Dashboard";
import type { DbStatus, HistoryEntry, MonthSummary, Report, Summary, Tower } from "@/lib/types";

// Recharts depende de layout real (ResponsiveContainer = 0px no jsdom); aqui validamos dados/estado, não SVG.
vi.mock("recharts", async () => {
  const React = await import("react");
  const Stub = ({ children }: { children?: React.ReactNode }) => React.createElement("div", null, children);
  const names = ["Area", "Bar", "BarChart", "CartesianGrid", "Cell", "ComposedChart", "Legend", "Line", "Pie", "PieChart",
    "ReferenceLine", "ResponsiveContainer", "Tooltip", "XAxis", "YAxis"];
  return Object.fromEntries(names.map((n) => [n, Stub]));
});

const summary: Summary = {
  config: { planName: "Claude Code Pro", resetDayOfWeek: 1, resetTime: "09:00", timezone: "America/Sao_Paulo", weeklyLimitTokens: 1_000_000, monthlyLimitTokens: null, countCacheReads: false },
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

const monthData: MonthSummary = {
  config: summary.config,
  period: { start: "2026-10-01T03:00:00Z", end: "2026-11-01T03:00:00Z", daysInMonth: 31, dayOfMonth: 2, secondsRemaining: 2_505_600, elapsedPct: 6.5 },
  limit: 7_000_000, limitEstimated: true, used: 350_000, remaining: 6_650_000, usedPct: 5,
  totals: { input: 10, output: 20, cacheCreation: 30, cacheRead: 40, messages: 9 },
  projection: { projectedTotal: 5_400_000, projectedPct: 77, dailyAverage: 175_000, willExceed: false, exhaustionAt: null },
  daily: Array.from({ length: 31 }, (_, i) => ({ index: i + 1, date: `2026-10-${String(i + 1).padStart(2, "0")}`, tokens: i < 2 ? 175_000 : 0, cumulative: Math.min(i + 1, 2) * 175_000 })),
  byProcess: [{ name: "token-control", tokens: 350_000, messages: 9, pct: 100 }],
  byModel: [{ name: "claude-sonnet-5-5", tokens: 350_000, messages: 9, pct: 100 }],
};

const towerData: Tower = {
  generatedAt: "2026-10-02T18:00:00Z", overall: "danger",
  counts: { ok: 2, active: 0, queued: 1, warn: 0, danger: 1 },
  items: [
    { id: "database", category: "health", label: "Banco de dados Neon (Postgres)", status: "danger", detail: "Sem conexão com o banco." },
    { id: "collector", category: "auto", label: "Coletor de consumo (Claude Code)", status: "queued", detail: "Nenhum envio recebido ainda." },
  ],
};

const reportData: Report = {
  generatedAt: "2026-10-03T15:00:00Z", title: "Relatório de análise — Controle de Tokens Claude Code", timezone: "America/Sao_Paulo",
  sections: [
    { title: "Resumo executivo", paragraphs: ["Estado geral da operação: operação normal."], bullets: [], table: null },
    { title: "Quem consome (mês atual)", paragraphs: [], bullets: ["Por modelo: claude-sonnet-5-5 350 mil (100%)."],
      table: { caption: "Consumo por processo no mês (top 8)", headers: ["Processo", "Tokens", "%", "Mensagens"], rows: [["token-control", "350 mil", "100%", "9"]] } },
    { title: "Recomendações", paragraphs: [], bullets: ["Ajuste o limite semanal para o valor real do seu plano."], table: null },
  ],
};

const db: DbStatus = {
  connected: true, latencyMs: 12, version: "17.2", databaseSizeBytes: 52_428_800,
  connections: { total: 3, active: 1, max: 100 },
  endpoint: { neon: true, pooled: false, endpointId: "ep-cool-1", region: "sa-east-1" },
  migrations: { status: "OK", latest: "1", failed: 0 },
  tables: [{ name: "token_usage_entries", rows: 1200, sizeBytes: 2_097_152 }],
  neonApi: { enabled: false, ok: false, error: null, project: null, branches: [], endpoints: [] },
};

function mockFetch(responses: Record<string, unknown>, status = 200) {
  vi.stubGlobal("fetch", vi.fn(async (url: string) => {
    const key = Object.keys(responses).find((k) => String(url).includes(k));
    return new Response(JSON.stringify(key ? responses[key] : { error: "x" }), { status: key ? status : 404 });
  }));
}

describe("Dashboard", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("mostra uso, tempo até o reset, projeção e detalhe por processo", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/db": db });
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
      "tokens/db": db,
    });
    render(<Dashboard />);
    await screen.findByText(/Nenhum consumo neste período/);
    expect(screen.getByText("Dentro do ritmo")).toBeInTheDocument();
    expect(screen.getByText(/aguardando 1h de dados/)).toBeInTheDocument();
  });

  it("aba Mês atual (além da semana): consumo do dia 1 até hoje, o que falta e referência estimada", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/month": monthData, "tokens/db": db });
    render(<Dashboard />);
    await screen.findByText(/Uso do ciclo atual/); // semana continua sendo a aba padrão
    expect(screen.getByText(/Histórico dos últimos ciclos/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Mês atual" }));
    const panel = await screen.findByRole("tabpanel", { name: "Mês atual" });
    expect(within(panel).getByText(/Uso do mês atual/)).toBeInTheDocument();
    expect(within(panel).getByText(/350 mil de 7 mi/)).toBeInTheDocument();
    expect(within(panel).getByText(/referência estimada/)).toBeInTheDocument();
    expect(within(panel).getByText("Fim do mês")).toBeInTheDocument();
    expect(within(panel).getByText("Restante no mês")).toBeInTheDocument();
    expect(within(panel).getByText("6,7 mi")).toBeInTheDocument(); // quanto falta
    expect(within(panel).getByText("29d 0h")).toBeInTheDocument();
    expect(within(panel).getByText("Consumo por dia do mês")).toBeInTheDocument();
    expect(screen.queryByText(/Histórico dos últimos ciclos/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "Semana (ciclo)" }));
    expect(await screen.findByText(/Uso do ciclo atual/)).toBeInTheDocument();
  });

  it("botão Gerar relatório de análise: um clique abre o relatório em português com imprimir, baixar e fechar", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/report": reportData, "tokens/db": db });
    const createUrl = vi.fn(() => "blob:fake");
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: vi.fn() }));
    const clicked: string[] = [];
    const orig = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function (this: HTMLAnchorElement) { clicked.push(this.download); };
    try {
      render(<Dashboard />);
      const button = await screen.findByRole("button", { name: "Gerar relatório de análise" });
      fireEvent.click(button);

      const dialog = await screen.findByRole("dialog", { name: "Relatório de análise" });
      expect(await within(dialog).findByText("Resumo executivo")).toBeInTheDocument();
      expect(within(dialog).getByText("Recomendações")).toBeInTheDocument();
      expect(within(dialog).getByRole("table", { name: "Consumo por processo no mês (top 8)" })).toBeInTheDocument();
      expect(within(dialog).getByText(/Ajuste o limite semanal/)).toBeInTheDocument();

      fireEvent.click(within(dialog).getByRole("button", { name: "Baixar (.md)" }));
      expect(createUrl).toHaveBeenCalledTimes(1);
      expect(clicked).toEqual(["relatorio-controle-de-tokens-2026-10-03.md"]);

      fireEvent.keyDown(document, { key: "Escape" });
      await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    } finally {
      HTMLAnchorElement.prototype.click = orig;
    }
  });

  it("relatório com falha mostra o erro e permite tentar novamente; Fechar fecha o painel", async () => {
    let calls = 0;
    vi.stubGlobal("fetch", vi.fn(async (url: string) => {
      const u = String(url);
      if (u.includes("tokens/report")) {
        calls++;
        return calls === 1 ? new Response(JSON.stringify({ error: "backend_unavailable" }), { status: 502 }) : new Response(JSON.stringify(reportData), { status: 200 });
      }
      const body = u.includes("summary") ? summary : u.includes("history") ? history : u.includes("tokens/db") ? db : { error: "x" };
      return new Response(JSON.stringify(body), { status: 200 });
    }));
    render(<Dashboard />);
    fireEvent.click(await screen.findByRole("button", { name: "Gerar relatório de análise" }));
    const dialog = await screen.findByRole("dialog", { name: "Relatório de análise" });
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Não foi possível gerar o relatório: backend_unavailable");
    expect(within(dialog).getByRole("button", { name: "Baixar (.md)" })).toBeDisabled();
    fireEvent.click(within(dialog).getByRole("button", { name: "Tentar novamente" }));
    expect(await within(dialog).findByText("Resumo executivo")).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "Fechar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("Torre: selo no cabeçalho em qualquer aba e aba própria com os processos", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/tower": towerData, "tokens/db": db });
    render(<Dashboard />);
    const badge = await screen.findByRole("button", { name: "Torre: Crítico" }); // visível já na aba Semana
    fireEvent.click(badge);
    const panel = await screen.findByRole("tabpanel", { name: "Torre de Controle dos Processos" });
    expect(within(panel).getByText("Banco de dados Neon (Postgres)")).toBeInTheDocument();
    expect(within(panel).getByText("Sem conexão com o banco.")).toBeInTheDocument();
    expect(screen.queryByText(/Histórico dos últimos ciclos/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Semana (ciclo)" }));
    expect(await screen.findByText(/Uso do ciclo atual/)).toBeInTheDocument();
  });

  it("falha só em /tower aparece na aba da torre e não derruba as outras", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/db": db });
    render(<Dashboard />);
    await screen.findByText(/Uso do ciclo atual/);
    expect(screen.queryByRole("button", { name: /Torre:/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Torre de Controle" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível carregar a torre");
  });

  it("falha só no /month aparece na aba do mês e não derruba a semana", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/db": db });
    render(<Dashboard />);
    await screen.findByText(/Uso do ciclo atual/);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("tab", { name: "Mês atual" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível carregar o mês");
  });

  it("card do Neon mostra saúde, aviso de conexão direta e orientação da API do Neon", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/db": db });
    render(<Dashboard />);
    const card = await screen.findByRole("region", { name: "Banco de dados Neon" });
    expect(within(card).getByText("Conectado")).toBeInTheDocument();
    expect(within(card).getByText(/ep-cool-1 · sa-east-1 · direto/)).toBeInTheDocument();
    expect(within(card).getByText(/50 MB/)).toBeInTheDocument();
    expect(within(card).getByRole("note")).toHaveTextContent("-pooler");
    expect(within(card).getByText(/NEON_API_KEY/)).toBeInTheDocument();
    expect(within(card).getByRole("table", { name: "Tabelas do app" })).toBeInTheDocument();
  });

  it("banco desconectado ou migration falha vira alerta vermelho; falha do /db não derruba o painel de consumo", async () => {
    mockFetch({ "tokens/summary": summary, "tokens/history": history, "tokens/db": { ...db, connected: false, migrations: { status: "UNKNOWN", latest: null, failed: 0 } } });
    const { unmount } = render(<Dashboard />);
    const card = await screen.findByRole("region", { name: "Banco de dados Neon" });
    expect(within(card).getByText("Desconectado")).toBeInTheDocument();
    unmount();

    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      String(url).includes("tokens/db")
        ? new Response(JSON.stringify({ error: "backend_unavailable" }), { status: 502 })
        : new Response(JSON.stringify(String(url).includes("summary") ? summary : history), { status: 200 })));
    render(<Dashboard />);
    await screen.findByText(/Uso do ciclo atual/);
    await screen.findByText(/Falha ao consultar o banco: backend_unavailable/);
  });

  it("erro do backend aparece como alerta, sem quebrar", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "backend_unavailable" }), { status: 502 })));
    render(<Dashboard />);
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("backend_unavailable"));
  });
});
