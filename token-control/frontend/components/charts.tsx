"use client";

import {
  Area, Bar, BarChart, CartesianGrid, Cell, ComposedChart, Legend, Line, Pie, PieChart, ReferenceLine,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { formatDay, formatPct, formatTokens } from "@/lib/format";
import type { Day, HistoryEntry, Slice } from "@/lib/types";

export const SERIES = ["var(--s1)", "var(--s2)", "var(--s3)", "var(--s4)", "var(--s5)", "var(--s6)"];
const AXIS = { fontSize: 12, fill: "var(--muted)" };
const tip = { contentStyle: { background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 8 } };
const tokenFmt = (v: unknown) => formatTokens(Number(v));

export function DailyChart({ daily }: { daily: Day[] }) {
  const data = daily.map((d) => ({ name: formatDay(d.date), tokens: d.tokens }));
  return (
    <div className="chart" role="img" aria-label="Tokens consumidos por dia do ciclo">
      <ResponsiveContainer>
        <BarChart data={data}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis dataKey="name" tick={AXIS} />
          <YAxis tick={AXIS} tickFormatter={tokenFmt} width={68} />
          <Tooltip {...tip} formatter={(v) => [tokenFmt(v), "Tokens"]} />
          <Bar isAnimationActive={false} dataKey="tokens" name="Tokens/dia" fill="var(--s1)" radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Acumulado real vs. ritmo linear ideal até o limite — mostra se o ciclo está adiantado ou folgado. */
export function CumulativeChart({ daily, limit, todayIndex }: { daily: Day[]; limit: number; todayIndex: number }) {
  const data = daily.map((d) => ({
    name: formatDay(d.date),
    acumulado: d.index <= todayIndex ? d.cumulative : null,
    ideal: Math.round((limit * d.index) / daily.length),
  }));
  return (
    <div className="chart" role="img" aria-label="Consumo acumulado versus ritmo ideal até o limite semanal">
      <ResponsiveContainer>
        <ComposedChart data={data}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis dataKey="name" tick={AXIS} />
          <YAxis tick={AXIS} tickFormatter={tokenFmt} width={68} domain={[0, Math.max(limit, 1)]} />
          <Tooltip {...tip} formatter={(v) => tokenFmt(v)} />
          <Legend />
          <ReferenceLine y={limit} stroke="var(--danger)" strokeDasharray="4 4" label={{ value: "Limite", fill: "var(--danger)", fontSize: 12, position: "insideTopLeft" }} />
          <Area isAnimationActive={false} type="monotone" dataKey="acumulado" name="Acumulado" stroke="var(--s1)" fill="var(--s1)" fillOpacity={0.2} connectNulls={false} />
          <Line isAnimationActive={false} type="monotone" dataKey="ideal" name="Ritmo ideal" stroke="var(--s2)" strokeDasharray="6 4" dot={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

export function ProcessChart({ slices }: { slices: Slice[] }) {
  const data = slices.slice(0, 10).map((s) => ({ name: s.name, tokens: s.tokens }));
  return (
    <div className="chart" role="img" aria-label="Tokens por processo" style={{ height: Math.max(220, data.length * 34 + 40) }}>
      <ResponsiveContainer>
        <BarChart data={data} layout="vertical" margin={{ left: 8 }}>
          <CartesianGrid stroke="var(--border)" horizontal={false} />
          <XAxis type="number" tick={AXIS} tickFormatter={tokenFmt} />
          <YAxis type="category" dataKey="name" tick={AXIS} width={150} />
          <Tooltip {...tip} formatter={(v) => [tokenFmt(v), "Tokens"]} />
          <Bar isAnimationActive={false} dataKey="tokens" fill="var(--s3)" radius={[0, 4, 4, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function ModelChart({ slices }: { slices: Slice[] }) {
  const data = slices.map((s) => ({ name: s.name, tokens: s.tokens }));
  return (
    <div className="chart" role="img" aria-label="Tokens por modelo">
      <ResponsiveContainer>
        <PieChart>
          <Pie data={data} dataKey="tokens" nameKey="name" innerRadius={60} outerRadius={100} paddingAngle={2} isAnimationActive={false} stroke="var(--surface)">
            {data.map((_, i) => <Cell key={i} fill={SERIES[i % SERIES.length]} />)}
          </Pie>
          <Tooltip {...tip} formatter={(v) => tokenFmt(v)} />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

export function HistoryChart({ history, timeZone }: { history: HistoryEntry[]; timeZone: string }) {
  const fmt = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", timeZone });
  const data = history.map((h) => ({ name: fmt.format(new Date(h.start)), tokens: h.used, pct: h.usedPct, current: h.current }));
  const limit = history[0]?.limit ?? 0;
  return (
    <div className="chart" role="img" aria-label="Consumo por ciclo semanal">
      <ResponsiveContainer>
        <BarChart data={data}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis dataKey="name" tick={AXIS} />
          <YAxis tick={AXIS} tickFormatter={tokenFmt} width={68} />
          <Tooltip {...tip} formatter={(v, _n, item) => [`${tokenFmt(v)} (${formatPct(item.payload.pct)})`, "Usado"]} labelFormatter={(l) => `Ciclo iniciado em ${l}`} />
          {limit > 0 && <ReferenceLine y={limit} stroke="var(--danger)" strokeDasharray="4 4" />}
          <Bar isAnimationActive={false} dataKey="tokens" radius={[4, 4, 0, 0]}>
            {data.map((d, i) => <Cell key={i} fill={d.current ? "var(--s1)" : "var(--s5)"} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
