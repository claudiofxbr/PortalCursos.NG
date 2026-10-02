"use client";

import { formatBytes, formatDuration } from "@/lib/format";
import type { DbStatus } from "@/lib/types";

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <tr><th scope="row" style={{ textAlign: "left", fontWeight: 500, color: "var(--muted)" }}>{label}</th><td>{value}</td></tr>
  );
}

/** Controle do banco Neon: saúde via SQL (sempre) + consumo/branches/computação via API do Neon (opcional). */
export default function NeonCard({ db, error }: { db: DbStatus | null; error: string | null }) {
  if (!db) {
    return (
      <section className="card grid" aria-label="Banco de dados Neon">
        <h2>Banco de dados Neon</h2>
        {error ? <p className="error" role="alert">Falha ao consultar o banco: {error}</p> : <p className="sub">Carregando…</p>}
      </section>
    );
  }

  const ep = db.endpoint;
  const connPct = db.connections.max > 0 ? Math.round((db.connections.total / db.connections.max) * 100) : 0;
  const state = !db.connected || db.migrations.status === "FAILED" ? "danger" : db.latencyMs > 1000 ? "warn" : "ok";
  const label = !db.connected ? "Desconectado" : db.migrations.status === "FAILED" ? "Migration falhou" : state === "warn" ? "Lento" : "Conectado";
  const api = db.neonApi;

  return (
    <section className="card grid" aria-label="Banco de dados Neon">
      <h2>Banco de dados Neon <span className={`badge ${state}`}>{label}</span></h2>
      {ep.neon && !ep.pooled && (
        <p className="sub" role="note">Conexão direta: para o tráfego da aplicação prefira a URL <code>-pooler</code> (use a direta só nas migrations — <code>NEON_DIRECT_URL</code>).</p>
      )}
      <table aria-label="Saúde do banco" style={{ borderCollapse: "collapse", fontSize: "0.9rem" }}>
        <tbody>
          <Row label="Endpoint" value={ep.neon ? `${ep.endpointId}${ep.region ? ` · ${ep.region}` : ""} · ${ep.pooled ? "pooled" : "direto"}` : "não-Neon (local)"} />
          <Row label="Postgres" value={db.version ?? "—"} />
          <Row label="Latência" value={`${db.latencyMs} ms`} />
          <Row label="Tamanho do banco" value={formatBytes(db.databaseSizeBytes)} />
          <Row label="Conexões" value={`${db.connections.total} de ${db.connections.max} (${connPct}%) · ${db.connections.active} ativas`} />
          <Row label="Migrations" value={`${db.migrations.status}${db.migrations.latest ? ` · v${db.migrations.latest}` : ""}${db.migrations.failed ? ` · ${db.migrations.failed} com falha` : ""}`} />
        </tbody>
      </table>

      {db.tables.length > 0 && (
        <table className="proc" aria-label="Tabelas do app">
          <thead><tr><th>Tabela</th><th className="num">Linhas (est.)</th><th className="num">Tamanho</th></tr></thead>
          <tbody>
            {db.tables.map((t) => (
              <tr key={t.name}><td>{t.name}</td><td className="num">{new Intl.NumberFormat("pt-BR").format(t.rows)}</td><td className="num">{formatBytes(t.sizeBytes)}</td></tr>
            ))}
          </tbody>
        </table>
      )}

      {api?.enabled ? (
        api.ok && api.project ? (
          <div aria-label="Consumo no Neon">
            <h2 style={{ marginTop: 8 }}>Projeto {api.project.name ?? ""} <span className="sub">(API do Neon)</span></h2>
            <table style={{ borderCollapse: "collapse", fontSize: "0.9rem" }}>
              <tbody>
                <Row label="Computação no período" value={api.project.computeTimeSeconds != null ? formatDuration(api.project.computeTimeSeconds) : "—"} />
                <Row label="Dados escritos" value={api.project.writtenDataBytes != null ? formatBytes(api.project.writtenDataBytes) : "—"} />
                <Row label="Transferência" value={api.project.dataTransferBytes != null ? formatBytes(api.project.dataTransferBytes) : "—"} />
                <Row label="Branches" value={api.branches.map((b) => `${b.name}${b.primary ? " (principal)" : ""}${b.state ? ` · ${b.state}` : ""}`).join(", ") || "—"} />
                <Row label="Computes" value={api.endpoints.map((e) => `${e.id} · ${e.state ?? "?"}${e.minCu != null && e.maxCu != null ? ` · ${e.minCu}–${e.maxCu} CU` : ""}`).join(", ") || "—"} />
              </tbody>
            </table>
          </div>
        ) : (
          <p className="error" role="alert">API do Neon: {api.error ?? "indisponível"}</p>
        )
      ) : (
        <p className="sub">Consumo e branches do projeto: defina <code>NEON_API_KEY</code> e <code>NEON_PROJECT_ID</code> para habilitar (somente leitura).</p>
      )}
    </section>
  );
}
