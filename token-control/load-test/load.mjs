#!/usr/bin/env node
// Teste de carga/estresse do backend: mistura ingestão (POST /usage, ids únicos + reenvio duplicado)
// e leituras (GET /summary, /history) em estágios crescentes de concorrência.
// Uso: TOKEN_CONTROL_URL=http://127.0.0.1:8090 TOKEN_CONTROL_API_KEY=... node load.mjs [--stages 5,20,50] [--seconds 10] [--p95 800]
// Sai com código 1 se houver erro >1% ou p95 acima do limite em QUALQUER estágio.
const arg = (n, d) => { const i = process.argv.indexOf(`--${n}`); return i > 0 ? process.argv[i + 1] : d; };
const URL_ = (process.env.TOKEN_CONTROL_URL || "http://127.0.0.1:8090").replace(/\/$/, "");
const KEY = process.env.TOKEN_CONTROL_API_KEY;
if (!KEY) { console.error("Defina TOKEN_CONTROL_API_KEY"); process.exit(2); }
const stages = arg("stages", "5,20,50").split(",").map(Number);
const seconds = Number(arg("seconds", "10"));
const maxP95 = Number(arg("p95", "800"));
const run = Date.now().toString(36);
let seq = 0;

const headers = { "Content-Type": "application/json", "X-API-Key": KEY };
const pct = (a, p) => a.length ? a[Math.min(a.length - 1, Math.floor((p / 100) * a.length))] : 0;

function batch(n) {
  const now = Date.now();
  return { entries: Array.from({ length: n }, () => ({
    messageId: `load-${run}-${seq++}`, occurredAt: new Date(now - Math.random() * 3_600_000).toISOString(),
    sessionId: "load", process: `proc-${seq % 7}`, model: `model-${seq % 3}`,
    inputTokens: 100, outputTokens: 50, cacheCreationTokens: 10, cacheReadTokens: 1000 })) };
}

async function worker(deadline, stat) {
  while (Date.now() < deadline) {
    const r = Math.random();
    const t0 = performance.now();
    let res;
    try {
      if (r < 0.5) res = await fetch(`${URL_}/api/tokens/usage`, { method: "POST", headers, body: JSON.stringify(batch(50)) });
      else if (r < 0.9) res = await fetch(`${URL_}/api/tokens/summary`, { headers });
      else res = await fetch(`${URL_}/api/tokens/history?cycles=8`, { headers });
      await res.arrayBuffer();
      stat.lat.push(performance.now() - t0);
      if (!res.ok) stat.err++;
    } catch { stat.err++; }
    stat.total++;
  }
}

let failed = false;
for (const c of stages) {
  const stat = { lat: [], err: 0, total: 0 };
  const deadline = Date.now() + seconds * 1000;
  await Promise.all(Array.from({ length: c }, () => worker(deadline, stat)));
  stat.lat.sort((a, b) => a - b);
  const errPct = (stat.err / Math.max(1, stat.total)) * 100;
  const p95 = pct(stat.lat, 95);
  const ok = errPct <= 1 && p95 <= maxP95;
  failed ||= !ok;
  console.log(`${ok ? "OK  " : "FAIL"} conc=${c} req=${stat.total} rps=${(stat.total / seconds).toFixed(0)} ` +
    `p50=${pct(stat.lat, 50).toFixed(0)}ms p95=${p95.toFixed(0)}ms p99=${pct(stat.lat, 99).toFixed(0)}ms erro=${errPct.toFixed(2)}%`);
}
process.exit(failed ? 1 : 0);
