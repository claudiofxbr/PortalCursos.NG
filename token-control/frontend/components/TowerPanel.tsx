"use client";

import { formatDateTime } from "@/lib/format";
import type { Tower, TowerCategory, TowerStatus } from "@/lib/types";

/** Rótulos em texto: o estado nunca depende só de cor. */
export const STATUS_LABEL: Record<TowerStatus, string> = {
  ok: "OK", active: "ativo", queued: "na fila", warn: "atenção", danger: "crítico",
};
const STATUS_ICON: Record<TowerStatus, string> = { ok: "✓", active: "●", queued: "…", warn: "!", danger: "✕" };
const BADGE_CLASS: Record<TowerStatus, string> = { ok: "ok", active: "ok", queued: "", warn: "warn", danger: "danger" };

const SECTIONS: { category: TowerCategory; title: string; empty: string }[] = [
  { category: "work", title: "Em andamento", empty: "Nenhum processo consumiu tokens nas últimas 5 horas." },
  { category: "queue", title: "Fila / pendências", empty: "Nenhuma pendência." },
  { category: "health", title: "Saúde do sistema", empty: "" },
  { category: "auto", title: "Processos automáticos recorrentes", empty: "" },
];

export const OVERALL_TITLE = { ok: "Operação normal", warn: "Atenção", danger: "Crítico" } as const;

export function overallSummary(t: Tower): string {
  if (t.overall === "danger") return `${t.counts.danger} item(ns) crítico(s)${t.counts.warn ? ` e ${t.counts.warn} em atenção` : ""}.`;
  if (t.overall === "warn") return `${t.counts.warn} item(ns) pedem acompanhamento.`;
  return `Tudo dentro do esperado${t.counts.queued ? ` · ${t.counts.queued} pendência(s) na fila` : ""}.`;
}

export default function TowerPanel({ tower, timeZone }: { tower: Tower; timeZone: string }) {
  return (
    <div role="tabpanel" aria-label="Torre de Controle dos Processos">
      <section className="card grid" aria-label="Estado geral">
        <h2>
          Torre de Controle dos Processos{" "}
          <span className={`badge ${BADGE_CLASS[tower.overall]}`}>{OVERALL_TITLE[tower.overall]}</span>
        </h2>
        <div className="sub">
          {overallSummary(tower)} Atualizado em {formatDateTime(tower.generatedAt, timeZone)}. Só mostra o que está
          ativo, pendente ou em alerta; os processos recorrentes ficam sempre visíveis.
        </div>
      </section>

      {SECTIONS.map(({ category, title, empty }) => {
        const items = tower.items.filter((i) => i.category === category);
        if (items.length === 0 && !empty) return null;
        return (
          <section key={category} className="card grid" aria-label={title}>
            <h2>{title} <span className="sub">({items.length})</span></h2>
            {items.length === 0 ? (
              <p className="sub">{empty}</p>
            ) : (
              <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: 10 }}>
                {items.map((i) => (
                  <li key={i.id} data-status={i.status} style={{ display: "grid", gap: 2 }}>
                    <div>
                      <span className={`badge ${BADGE_CLASS[i.status]}`}>{STATUS_ICON[i.status]} {STATUS_LABEL[i.status]}</span>{" "}
                      <strong>{i.label}</strong>
                    </div>
                    <div className="sub">{i.detail}</div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}
