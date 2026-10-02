#!/usr/bin/env node
// Collector do Controle de Tokens Claude Code.
// Lê ~/.claude/projects/**/*.jsonl, extrai SOMENTE message.usage + metadados (nunca o texto das
// conversas) e envia em lotes idempotentes (messageId único) para POST /api/tokens/usage.
import { createReadStream } from "node:fs";
import { readdir, stat } from "node:fs/promises";
import { homedir } from "node:os";
import { basename, join } from "node:path";
import { createInterface } from "node:readline";
import { pathToFileURL } from "node:url";

const BATCH_SIZE = 500;
const MAX_MESSAGE_ID = 128;

export async function* walk(dir) {
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    const full = join(dir, e.name);
    if (e.isDirectory()) yield* walk(full);
    else if (e.isFile() && e.name.endsWith(".jsonl")) yield full;
  }
}

/** Converte uma linha do transcrito em entrada de uso, ou null se não houver uso contabilizável. */
export function parseLine(line, fallbackProcess = "desconhecido") {
  if (!line || !line.includes('"usage"')) return null;
  let d;
  try {
    d = JSON.parse(line);
  } catch {
    return null;
  }
  const m = d?.message;
  const u = m?.usage;
  if (!m || !u || typeof m.id !== "string" || !d.timestamp) return null;
  if (!m.model || m.model === "<synthetic>") return null;
  const occurredAt = new Date(d.timestamp);
  if (Number.isNaN(occurredAt.getTime())) return null;

  const base = d.cwd ? basename(d.cwd) : fallbackProcess;
  const num = (v) => (Number.isFinite(v) && v > 0 ? Math.floor(v) : 0);
  return {
    messageId: m.id.slice(0, MAX_MESSAGE_ID),
    occurredAt: occurredAt.toISOString(),
    sessionId: typeof d.sessionId === "string" ? d.sessionId.slice(0, 64) : null,
    process: (d.isSidechain ? `${base} · subagente` : base).slice(0, 120),
    model: String(m.model).slice(0, 80),
    inputTokens: num(u.input_tokens),
    outputTokens: num(u.output_tokens),
    cacheCreationTokens: num(u.cache_creation_input_tokens),
    cacheReadTokens: num(u.cache_read_input_tokens),
  };
}

/** Lê um arquivo e devolve Map messageId -> entrada. Respostas em streaming repetem o id: vale a mais completa. */
export async function readFileEntries(file, fallbackProcess, since) {
  const out = new Map();
  const rl = createInterface({ input: createReadStream(file, { encoding: "utf8" }), crlfDelay: Infinity });
  for await (const line of rl) {
    const e = parseLine(line, fallbackProcess);
    if (!e) continue;
    if (since && new Date(e.occurredAt) < since) continue;
    const prev = out.get(e.messageId);
    if (!prev || e.outputTokens >= prev.outputTokens) out.set(e.messageId, e);
  }
  return out;
}

export async function collect(root, since) {
  const all = new Map();
  for await (const file of walk(root)) {
    if (since) {
      const s = await stat(file);
      if (s.mtime < since) continue; // arquivo sem escrita desde o corte
    }
    const fallback = basename(join(file, ".."));
    for (const [id, e] of await readFileEntries(file, fallback, since)) {
      const prev = all.get(id);
      if (!prev || e.outputTokens >= prev.outputTokens) all.set(id, e);
    }
  }
  return [...all.values()];
}

export async function send(entries, { url, apiKey, fetchImpl = fetch }) {
  const total = { received: 0, inserted: 0, duplicates: 0 };
  for (let i = 0; i < entries.length; i += BATCH_SIZE) {
    const res = await fetchImpl(`${url.replace(/\/$/, "")}/api/tokens/usage`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-API-Key": apiKey },
      body: JSON.stringify({ entries: entries.slice(i, i + BATCH_SIZE) }),
    });
    if (!res.ok) throw new Error(`Backend respondeu ${res.status} no lote ${i / BATCH_SIZE + 1}`);
    const r = await res.json();
    total.received += r.received;
    total.inserted += r.inserted;
    total.duplicates += r.duplicates;
  }
  return total;
}

function parseArgs(argv) {
  const args = { days: 14, dryRun: false, watch: 0 };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--dry-run") args.dryRun = true;
    else if (argv[i] === "--days") args.days = Number(argv[++i]);
    else if (argv[i] === "--watch") args.watch = Number(argv[++i]);
    else if (argv[i] === "--dir") args.dir = argv[++i];
  }
  if (!Number.isFinite(args.days) || args.days <= 0) throw new Error("--days inválido");
  return args;
}

async function run(args) {
  const root = args.dir ?? process.env.CLAUDE_PROJECTS_DIR ?? join(homedir(), ".claude", "projects");
  const since = new Date(Date.now() - args.days * 86_400_000);
  const entries = await collect(root, since);
  if (args.dryRun) {
    console.log(`[dry-run] ${entries.length} mensagens com uso desde ${since.toISOString()} em ${root}`);
    return;
  }
  const url = process.env.TOKEN_CONTROL_URL;
  const apiKey = process.env.TOKEN_CONTROL_API_KEY;
  if (!url || !apiKey) throw new Error("Defina TOKEN_CONTROL_URL e TOKEN_CONTROL_API_KEY");
  const r = await send(entries, { url, apiKey });
  console.log(`enviadas=${r.received} novas=${r.inserted} duplicadas=${r.duplicates}`);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  const args = parseArgs(process.argv.slice(2));
  const once = () =>
    run(args).catch((e) => {
      console.error(`erro: ${e.message}`);
      if (!args.watch) process.exitCode = 1; // execução única: falha precisa ser visível para scripts (código != 0)
    });
  await once();
  if (args.watch > 0) setInterval(once, args.watch * 1000);
  else if (process.exitCode === undefined) process.exitCode = 0;
}
