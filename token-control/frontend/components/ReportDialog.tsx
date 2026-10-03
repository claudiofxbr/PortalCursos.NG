"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getReport } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { downloadMarkdown } from "@/lib/report";
import type { Report } from "@/lib/types";

/** Painel do "Relatório de análise": abre com um clique, em português, com Imprimir/PDF e Baixar (.md). */
export default function ReportDialog({ onClose }: { onClose: () => void }) {
  const [report, setReport] = useState<Report | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const dialogRef = useRef<HTMLDivElement>(null);
  // onClose vem inline do pai (muda a cada render): guardado em ref para o efeito rodar UMA vez ao abrir
  const closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setReport(await getReport());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao gerar o relatório");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- gera o relatório ao abrir o painel
    void load();
    dialogRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && closeRef.current();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [load]);

  return (
    <div className="report-overlay" role="presentation" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="report-print card" role="dialog" aria-modal="true" aria-label="Relatório de análise" tabIndex={-1} ref={dialogRef}>
        <div className="report-head no-print">
          <h2 style={{ margin: 0 }}>Relatório de análise</h2>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button className="btn" disabled={!report} onClick={() => window.print()}>Imprimir / salvar em PDF</button>
            <button className="btn ghost" disabled={!report} onClick={() => report && downloadMarkdown(report)}>Baixar (.md)</button>
            <button className="btn ghost" onClick={onClose}>Fechar</button>
          </div>
        </div>

        {loading && <p className="empty" role="status">Gerando o relatório…</p>}
        {error && (
          <div role="alert">
            <p className="error">Não foi possível gerar o relatório: {error}</p>
            <button className="btn" onClick={() => void load()}>Tentar novamente</button>
          </div>
        )}

        {report && (
          <article aria-label="Conteúdo do relatório">
            <h1 style={{ fontSize: "1.25rem" }}>{report.title}</h1>
            <p className="sub">Gerado em {formatDateTime(report.generatedAt, report.timezone)} ({report.timezone})</p>
            {report.sections.map((s) => (
              <section key={s.title} aria-label={s.title} style={{ marginTop: 16 }}>
                <h2>{s.title}</h2>
                {s.paragraphs.map((p, i) => <p key={i} style={{ margin: "6px 0" }}>{p}</p>)}
                {s.bullets.length > 0 && (
                  <ul style={{ margin: "6px 0", paddingLeft: 20 }}>
                    {s.bullets.map((b, i) => <li key={i} style={{ margin: "4px 0" }}>{b}</li>)}
                  </ul>
                )}
                {s.table && (
                  <table className="proc" aria-label={s.table.caption}>
                    <caption style={{ textAlign: "left", fontWeight: 600, padding: "6px 0" }}>{s.table.caption}</caption>
                    <thead><tr>{s.table.headers.map((h, i) => <th key={h} className={i > 0 ? "num" : undefined}>{h}</th>)}</tr></thead>
                    <tbody>
                      {s.table.rows.map((row, ri) => (
                        <tr key={ri}>{row.map((c, ci) => <td key={ci} className={ci > 0 ? "num" : undefined}>{c}</td>)}</tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </section>
            ))}
          </article>
        )}
      </div>
    </div>
  );
}
