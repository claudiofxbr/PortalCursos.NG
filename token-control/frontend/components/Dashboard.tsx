"use client";

import { useCallback, useEffect, useState } from "react";
import { getDbStatus, getHistory, getMonth, getSummary, getTower, saveConfig } from "@/lib/api";
import { formatPct, WEEKDAYS } from "@/lib/format";
import type { DbStatus, HistoryEntry, MonthSummary, PlanConfig, Summary, Tower } from "@/lib/types";
import ConfigForm from "./ConfigForm";
import { HistoryChart } from "./charts";
import NeonCard from "./NeonCard";
import PeriodPanel, { type PeriodView } from "./PeriodPanel";
import ReportDialog from "./ReportDialog";
import TowerPanel, { OVERALL_TITLE } from "./TowerPanel";

const REFRESH_MS = 60_000;

type Tab = "week" | "month" | "tower";

function weekView(s: Summary): PeriodView {
  const todayIndex = Math.min(7, Math.max(1, Math.ceil((s.cycle.elapsedPct / 100) * 7) || 1));
  return {
    title: "Uso do ciclo atual", used: s.used, limit: s.limit, remaining: s.remaining, usedPct: s.usedPct,
    totals: s.totals, projection: s.projection, daily: s.daily, byProcess: s.byProcess, byModel: s.byModel,
    elapsedPct: s.cycle.elapsedPct, elapsedLabel: "do ciclo decorrido", secondsRemaining: s.cycle.secondsRemaining,
    endAt: s.cycle.end, endLabel: "Próximo reset", remainingLabel: "Restante no ciclo",
    projectionLabel: "Projeção até o reset", dailyTitle: "Consumo por dia do ciclo", todayIndex,
    last5hTokens: s.last5hTokens, timezone: s.config.timezone,
  };
}

function monthView(m: MonthSummary): PeriodView {
  return {
    title: "Uso do mês atual", used: m.used, limit: m.limit, remaining: m.remaining, usedPct: m.usedPct,
    totals: m.totals, projection: m.projection, daily: m.daily, byProcess: m.byProcess, byModel: m.byModel,
    elapsedPct: m.period.elapsedPct, elapsedLabel: "do mês decorrido", secondsRemaining: m.period.secondsRemaining,
    endAt: m.period.end, endLabel: "Fim do mês", remainingLabel: "Restante no mês",
    projectionLabel: "Projeção até o fim do mês", dailyTitle: "Consumo por dia do mês", todayIndex: m.period.dayOfMonth,
    limitNote: m.limitEstimated ? "referência estimada: semanal × dias/7" : undefined, timezone: m.config.timezone,
  };
}

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [month, setMonth] = useState<MonthSummary | null>(null);
  const [monthError, setMonthError] = useState<string | null>(null);
  const [tower, setTower] = useState<Tower | null>(null);
  const [towerError, setTowerError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("week");
  const [showReport, setShowReport] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [db, setDb] = useState<DbStatus | null>(null);
  const [dbError, setDbError] = useState<string | null>(null);
  const [showConfig, setShowConfig] = useState(false);

  const load = useCallback(async () => {
    try {
      const [s, h] = await Promise.all([getSummary(), getHistory(8)]);
      setSummary(s);
      setHistory(h);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao carregar");
    }
    // torre em chamada separada: falha aqui não derruba as demais abas
    try {
      setTower(await getTower());
      setTowerError(null);
    } catch (e) {
      setTowerError(e instanceof Error ? e.message : "Falha ao carregar a torre");
    }
    // mês em chamada separada: falha aqui não derruba a aba semanal
    try {
      setMonth(await getMonth());
      setMonthError(null);
    } catch (e) {
      setMonthError(e instanceof Error ? e.message : "Falha ao carregar o mês");
    }
    // banco em chamada separada: falha aqui não derruba o painel de consumo (e vice-versa)
    try {
      setDb(await getDbStatus());
      setDbError(null);
    } catch (e) {
      setDbError(e instanceof Error ? e.message : "Falha ao consultar o banco");
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- carga inicial + polling
    void load();
    const id = setInterval(load, REFRESH_MS);
    return () => clearInterval(id);
  }, [load]);

  async function handleSave(cfg: PlanConfig) {
    await saveConfig(cfg);
    setShowConfig(false);
    await load();
  }

  if (!summary) {
    return (
      <main className="wrap">
        <h1>Controle de Tokens Claude Code</h1>
        {error ? <p className="error" role="alert">Não foi possível carregar: {error}</p> : <p className="empty">Carregando…</p>}
      </main>
    );
  }

  const { config } = summary;
  const view = tab === "week" ? weekView(summary) : tab === "month" && month ? monthView(month) : null;

  return (
    <main className="wrap">
      <header className="top">
        <div>
          <h1>Controle de Tokens Claude Code{tower && (
            <button className={`badge ${tower.overall === "danger" ? "danger" : tower.overall === "warn" ? "warn" : "ok"}`}
              style={{ marginLeft: 12, background: "transparent", cursor: "pointer", verticalAlign: "middle" }}
              onClick={() => setTab("tower")} title="Abrir a Torre de Controle dos Processos" aria-label={`Torre: ${OVERALL_TITLE[tower.overall]}`}>
              Torre: {OVERALL_TITLE[tower.overall]}
            </button>
          )}</h1>
          <div className="sub">
            {config.planName} · ciclo semanal reseta {WEEKDAYS[config.resetDayOfWeek]} {config.resetTime} ({config.timezone})
          </div>
        </div>
        <div>
          <button className="btn" aria-haspopup="dialog" onClick={() => setShowReport(true)}
            title="Gera uma análise do uso e da operação do app, em português">Gerar relatório de análise</button>{" "}
          <button className="btn ghost" onClick={() => setShowConfig((v) => !v)}>
            {showConfig ? "Fechar configuração" : "Configurar plano"}
          </button>{" "}
          <button className="btn ghost" onClick={() => void load()}>Atualizar</button>
        </div>
      </header>

      {error && <p className="error" role="alert">Falha ao atualizar: {error}</p>}

      {showConfig && (
        <section className="card grid" aria-label="Configuração">
          <h2>Configuração do plano</h2>
          <ConfigForm config={config} onSave={handleSave} />
          <div className="sub">O limite exato do Claude Code Pro não é publicado: ajuste o orçamento conforme o que você observa no /usage. Sem orçamento mensal, o mês usa a referência estimada (semanal × dias do mês ÷ 7).</div>
        </section>
      )}

      <div role="tablist" aria-label="Período" style={{ display: "flex", gap: 8, marginTop: 16 }}>
        <button role="tab" aria-selected={tab === "week"} className={`btn ${tab === "week" ? "" : "ghost"}`} onClick={() => setTab("week")}>Semana (ciclo)</button>
        <button role="tab" aria-selected={tab === "month"} className={`btn ${tab === "month" ? "" : "ghost"}`} onClick={() => setTab("month")}>Mês atual</button>
        <button role="tab" aria-selected={tab === "tower"} className={`btn ${tab === "tower" ? "" : "ghost"}`} onClick={() => setTab("tower")}>Torre de Controle</button>
      </div>

      {tab === "tower" ? (
        tower ? (
          <TowerPanel tower={tower} timeZone={config.timezone} />
        ) : (
          <p className={towerError ? "error" : "empty"} role={towerError ? "alert" : undefined}>
            {towerError ? `Não foi possível carregar a torre: ${towerError}` : "Carregando a torre…"}
          </p>
        )
      ) : view ? (
        <div role="tabpanel" aria-label={tab === "week" ? "Semana (ciclo)" : "Mês atual"}>
          <PeriodPanel v={view} />
        </div>
      ) : (
        <p className={monthError ? "error" : "empty"} role={monthError ? "alert" : undefined}>
          {monthError ? `Não foi possível carregar o mês: ${monthError}` : "Carregando o mês…"}
        </p>
      )}

      {tab === "week" && (
        <section className="card grid" aria-label="Histórico">
          <h2>Histórico dos últimos ciclos <span className="sub">(atual: {formatPct(summary.usedPct)})</span></h2>
          <HistoryChart history={history} timeZone={config.timezone} />
        </section>
      )}

      <NeonCard db={db} error={dbError} />
      {showReport && <ReportDialog onClose={() => setShowReport(false)} />}
    </main>
  );
}
