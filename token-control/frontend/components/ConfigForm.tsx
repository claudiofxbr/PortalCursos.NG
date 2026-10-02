"use client";

import { useState } from "react";
import { WEEKDAYS } from "@/lib/format";
import type { PlanConfig } from "@/lib/types";

export default function ConfigForm({ config, onSave }: { config: PlanConfig; onSave: (c: PlanConfig) => Promise<void> }) {
  const [form, setForm] = useState(config);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSave(form);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao salvar");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="cfg" onSubmit={submit} aria-label="Configuração do plano">
      <label>Dia do reset
        <select value={form.resetDayOfWeek} onChange={(e) => setForm({ ...form, resetDayOfWeek: Number(e.target.value) })}>
          {[1, 2, 3, 4, 5, 6, 7].map((d) => <option key={d} value={d}>{WEEKDAYS[d]}</option>)}
        </select>
      </label>
      <label>Hora do reset
        <input type="time" required value={form.resetTime} onChange={(e) => setForm({ ...form, resetTime: e.target.value })} />
      </label>
      <label>Fuso horário
        <input required value={form.timezone} onChange={(e) => setForm({ ...form, timezone: e.target.value })} />
      </label>
      <label>Orçamento semanal (tokens)
        <input type="number" min={1} required value={form.weeklyLimitTokens}
          onChange={(e) => setForm({ ...form, weeklyLimitTokens: Number(e.target.value) })} />
      </label>
      <label>Orçamento mensal (tokens) — opcional
        <input type="number" min={1} placeholder="vazio = estimado (semanal × dias/7)" value={form.monthlyLimitTokens ?? ""}
          onChange={(e) => setForm({ ...form, monthlyLimitTokens: e.target.value === "" ? null : Number(e.target.value) })} />
      </label>
      <label>
        <span><input type="checkbox" checked={form.countCacheReads}
          onChange={(e) => setForm({ ...form, countCacheReads: e.target.checked })} /> Contar leituras de cache</span>
      </label>
      <div>
        <button className="btn" type="submit" disabled={busy}>{busy ? "Salvando…" : "Salvar"}</button>
        {error && <div className="error" role="alert">{error}</div>}
      </div>
    </form>
  );
}
