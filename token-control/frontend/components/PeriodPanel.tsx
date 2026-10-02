"use client";

import { formatDateTime, formatDuration, formatPct, formatTokens, severity } from "@/lib/format";
import type { Day, Slice, Summary } from "@/lib/types";
import { CumulativeChart, DailyChart, ModelChart, ProcessChart, SERIES } from "./charts";

const SEVERITY_LABEL = { ok: "Dentro do ritmo", warn: "Atenção", danger: "Crítico" } as const;

/** Modelo comum às duas abas (semana/ciclo e mês atual) — a tela é a mesma, só mudam janela, rótulos e limite. */
export interface PeriodView {
  title: string;
  used: number;
  limit: number;
  remaining: number;
  usedPct: number;
  totals: Summary["totals"];
  projection: Summary["projection"];
  daily: Day[];
  byProcess: Slice[];
  byModel: Slice[];
  elapsedPct: number;
  elapsedLabel: string;
  secondsRemaining: number;
  endAt: string;
  endLabel: string;
  remainingLabel: string;
  projectionLabel: string;
  dailyTitle: string;
  todayIndex: number;
  limitNote?: string;
  last5hTokens?: number;
  timezone: string;
}

function Kpi({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="card kpi">
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      {note && <div className="note">{note}</div>}
    </div>
  );
}

export default function PeriodPanel({ v }: { v: PeriodView }) {
  const sev = severity(v.usedPct);
  const { projection } = v;
  const hasData = v.totals.messages > 0;

  return (
    <>
      <section className="card grid" aria-label={v.title}>
        <h2>{v.title} <span className={`badge ${sev}`}>{SEVERITY_LABEL[sev]}</span></h2>
        <div className="gauge">
          <div className="track" role="progressbar" aria-valuemin={0} aria-valuemax={100}
            aria-valuenow={Math.min(100, v.usedPct)} aria-label={`${v.title}: uso do orçamento`}>
            <div className={`fill ${sev}`} style={{ width: `${Math.min(100, v.usedPct)}%` }} />
            <div className="marker" style={{ left: `${v.elapsedPct}%` }} title="Tempo decorrido" />
          </div>
          <div className="legend">
            <span>{formatTokens(v.used)} de {formatTokens(v.limit)} ({formatPct(v.usedPct)}){v.limitNote ? ` · ${v.limitNote}` : ""}</span>
            <span>▏ marcador = {formatPct(v.elapsedPct)} {v.elapsedLabel}</span>
          </div>
        </div>
      </section>

      <section className="grid kpis" aria-label="Indicadores">
        <Kpi label={v.endLabel} value={formatDuration(v.secondsRemaining)} note={formatDateTime(v.endAt, v.timezone)} />
        <Kpi label={v.remainingLabel} value={formatTokens(v.remaining)} note="tokens do orçamento" />
        {v.last5hTokens !== undefined && <Kpi label="Últimas 5 horas" value={formatTokens(v.last5hTokens)} note="janela de sessão" />}
        <Kpi
          label={v.projectionLabel}
          value={projection ? formatTokens(projection.projectedTotal) : "—"}
          note={projection
            ? projection.willExceed && projection.exhaustionAt
              ? `esgota em ${formatDateTime(projection.exhaustionAt, v.timezone)}`
              : `${formatPct(projection.projectedPct)} do orçamento`
            : "aguardando 1h de dados"}
        />
        <Kpi label="Média diária" value={projection ? formatTokens(projection.dailyAverage) : "—"} note="no período atual" />
        <Kpi label="Mensagens" value={new Intl.NumberFormat("pt-BR").format(v.totals.messages)}
          note={`in ${formatTokens(v.totals.input)} · out ${formatTokens(v.totals.output)} · cache ${formatTokens(v.totals.cacheCreation + v.totals.cacheRead)}`} />
      </section>

      {!hasData ? (
        <p className="empty">Nenhum consumo neste período ainda. Rode o collector (<code>node collect.mjs</code>) para enviar o uso do Claude Code.</p>
      ) : (
        <>
          <section className="grid two">
            <div className="card"><h2>{v.dailyTitle}</h2><DailyChart daily={v.daily} /></div>
            <div className="card"><h2>Acumulado × ritmo ideal</h2><CumulativeChart daily={v.daily} limit={v.limit} todayIndex={v.todayIndex} /></div>
          </section>
          <section className="grid two">
            <div className="card">
              <h2>Consumo por processo</h2>
              <ProcessChart slices={v.byProcess} />
              <table className="proc" aria-label="Detalhe por processo">
                <thead><tr><th>Processo</th><th className="num">Tokens</th><th className="num">%</th><th className="num">Msgs</th></tr></thead>
                <tbody>
                  {v.byProcess.map((p) => (
                    <tr key={p.name}><td>{p.name}</td><td className="num">{formatTokens(p.tokens)}</td><td className="num">{formatPct(p.pct)}</td><td className="num">{p.messages}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="card">
              <h2>Consumo por modelo</h2>
              <ModelChart slices={v.byModel} />
              <ul aria-label="Detalhe por modelo" style={{ listStyle: "none", padding: 0, margin: "8px 0 0", fontSize: "0.85rem" }}>
                {v.byModel.map((m, i) => (
                  <li key={m.name}><span className="swatch" style={{ background: SERIES[i % SERIES.length] }} />{m.name} — {formatTokens(m.tokens)} ({formatPct(m.pct)})</li>
                ))}
              </ul>
            </div>
          </section>
        </>
      )}
    </>
  );
}
