"use client";

import { useCallback, useEffect, useState } from "react";
import { getDbStatus, getHistory, getSummary, saveConfig } from "@/lib/api";
import {
  formatDateTime, formatDuration, formatPct, formatTokens, severity, WEEKDAYS,
} from "@/lib/format";
import type { DbStatus, HistoryEntry, PlanConfig, Summary } from "@/lib/types";
import ConfigForm from "./ConfigForm";
import NeonCard from "./NeonCard";
import { CumulativeChart, DailyChart, HistoryChart, ModelChart, ProcessChart, SERIES } from "./charts";

const REFRESH_MS = 60_000;
const SEVERITY_LABEL = { ok: "Dentro do ritmo", warn: "Atenção", danger: "Crítico" } as const;

function Kpi({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="card kpi">
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      {note && <div className="note">{note}</div>}
    </div>
  );
}

export default function Dashboard() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
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

  const { cycle, config, projection } = summary;
  const sev = severity(summary.usedPct);
  const todayIndex = Math.min(7, Math.max(1, Math.ceil((cycle.elapsedPct / 100) * 7) || 1));
  const resetLabel = formatDateTime(cycle.end, config.timezone);
  const hasData = summary.totals.messages > 0;

  return (
    <main className="wrap">
      <header className="top">
        <div>
          <h1>Controle de Tokens Claude Code</h1>
          <div className="sub">
            {config.planName} · ciclo semanal reseta {WEEKDAYS[config.resetDayOfWeek]} {config.resetTime} ({config.timezone})
          </div>
        </div>
        <div>
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
          <div className="sub">O limite semanal exato do Claude Code Pro não é publicado: ajuste o orçamento conforme o que você observa no /usage.</div>
        </section>
      )}

      <section className="card grid" aria-label="Uso até o reset">
        <h2>Uso do ciclo atual <span className={`badge ${sev}`}>{SEVERITY_LABEL[sev]}</span></h2>
        <div className="gauge">
          <div className="track" role="progressbar" aria-valuemin={0} aria-valuemax={100}
            aria-valuenow={Math.min(100, summary.usedPct)} aria-label="Uso do orçamento semanal">
            <div className={`fill ${sev}`} style={{ width: `${Math.min(100, summary.usedPct)}%` }} />
            <div className="marker" style={{ left: `${cycle.elapsedPct}%` }} title="Tempo decorrido do ciclo" />
          </div>
          <div className="legend">
            <span>{formatTokens(summary.used)} de {formatTokens(summary.limit)} ({formatPct(summary.usedPct)})</span>
            <span>▏ marcador = {formatPct(cycle.elapsedPct)} do ciclo decorrido</span>
          </div>
        </div>
      </section>

      <section className="grid kpis" aria-label="Indicadores">
        <Kpi label="Próximo reset" value={formatDuration(cycle.secondsRemaining)} note={resetLabel} />
        <Kpi label="Restante no ciclo" value={formatTokens(summary.remaining)} note="tokens do orçamento" />
        <Kpi label="Últimas 5 horas" value={formatTokens(summary.last5hTokens)} note="janela de sessão" />
        <Kpi
          label="Projeção até o reset"
          value={projection ? formatTokens(projection.projectedTotal) : "—"}
          note={projection
            ? projection.willExceed && projection.exhaustionAt
              ? `esgota em ${formatDateTime(projection.exhaustionAt, config.timezone)}`
              : `${formatPct(projection.projectedPct)} do orçamento`
            : "aguardando 1h de dados"}
        />
        <Kpi label="Média diária" value={projection ? formatTokens(projection.dailyAverage) : "—"} note="no ciclo atual" />
        <Kpi label="Mensagens" value={new Intl.NumberFormat("pt-BR").format(summary.totals.messages)}
          note={`in ${formatTokens(summary.totals.input)} · out ${formatTokens(summary.totals.output)} · cache ${formatTokens(summary.totals.cacheCreation + summary.totals.cacheRead)}`} />
      </section>

      {!hasData ? (
        <p className="empty">Nenhum consumo neste ciclo ainda. Rode o collector (<code>npm run collect</code>) para enviar o uso do Claude Code.</p>
      ) : (
        <>
          <section className="grid two">
            <div className="card"><h2>Consumo por dia do ciclo</h2><DailyChart daily={summary.daily} /></div>
            <div className="card"><h2>Acumulado × ritmo ideal</h2><CumulativeChart daily={summary.daily} limit={summary.limit} todayIndex={todayIndex} /></div>
          </section>
          <section className="grid two">
            <div className="card">
              <h2>Consumo por processo</h2>
              <ProcessChart slices={summary.byProcess} />
              <table className="proc" aria-label="Detalhe por processo">
                <thead><tr><th>Processo</th><th className="num">Tokens</th><th className="num">%</th><th className="num">Msgs</th></tr></thead>
                <tbody>
                  {summary.byProcess.map((p) => (
                    <tr key={p.name}><td>{p.name}</td><td className="num">{formatTokens(p.tokens)}</td><td className="num">{formatPct(p.pct)}</td><td className="num">{p.messages}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="card">
              <h2>Consumo por modelo</h2>
              <ModelChart slices={summary.byModel} />
              <ul aria-label="Detalhe por modelo" style={{ listStyle: "none", padding: 0, margin: "8px 0 0", fontSize: "0.85rem" }}>
                {summary.byModel.map((m, i) => (
                  <li key={m.name}><span className="swatch" style={{ background: SERIES[i % SERIES.length] }} />{m.name} — {formatTokens(m.tokens)} ({formatPct(m.pct)})</li>
                ))}
              </ul>
            </div>
          </section>
        </>
      )}

      <section className="card grid" aria-label="Histórico">
        <h2>Histórico dos últimos ciclos</h2>
        <HistoryChart history={history} timeZone={config.timezone} />
      </section>

      <NeonCard db={db} error={dbError} />
    </main>
  );
}
