import assert from "node:assert/strict";
import { createServer } from "node:http";
import { mkdtemp, mkdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { test } from "node:test";
import { collect, parseLine, send } from "../collect.mjs";

const line = (over = {}, msg = {}) =>
  JSON.stringify({
    timestamp: "2026-10-01T10:00:00.000Z",
    sessionId: "sess-1",
    cwd: "/home/user/PortalCursos.NG",
    message: {
      id: "msg_1",
      model: "claude-sonnet-5-5",
      content: [{ type: "text", text: "SEGREDO NUNCA ENVIAR" }],
      usage: { input_tokens: 3, output_tokens: 10, cache_creation_input_tokens: 5, cache_read_input_tokens: 7 },
      ...msg,
    },
    ...over,
  });

test("parseLine extrai só contagens e metadados, nunca conteúdo", () => {
  const e = parseLine(line());
  assert.deepEqual(e, {
    messageId: "msg_1",
    occurredAt: "2026-10-01T10:00:00.000Z",
    sessionId: "sess-1",
    process: "PortalCursos.NG",
    model: "claude-sonnet-5-5",
    inputTokens: 3,
    outputTokens: 10,
    cacheCreationTokens: 5,
    cacheReadTokens: 7,
  });
  assert.ok(!JSON.stringify(e).includes("SEGREDO"));
});

test("parseLine ignora lixo, sintético, sem id e timestamp inválido", () => {
  assert.equal(parseLine("não é json com \"usage\""), null);
  assert.equal(parseLine(""), null);
  assert.equal(parseLine(line({}, { model: "<synthetic>" })), null);
  assert.equal(parseLine(line({}, { id: undefined })), null);
  assert.equal(parseLine(line({ timestamp: "ontem" })), null);
});

test("parseLine marca subagentes e normaliza números inválidos", () => {
  const e = parseLine(line({ isSidechain: true }, { usage: { input_tokens: -4, output_tokens: "x" } }));
  assert.equal(e.process, "PortalCursos.NG · subagente");
  assert.equal(e.inputTokens, 0);
  assert.equal(e.outputTokens, 0);
});

test("collect deduplica por message.id mantendo a resposta mais completa e respeita --since", async () => {
  const root = await mkdtemp(join(tmpdir(), "tc-"));
  await mkdir(join(root, "proj-a"));
  await writeFile(
    join(root, "proj-a", "a.jsonl"),
    [
      line({}, { usage: { input_tokens: 1, output_tokens: 2 } }), // parcial (streaming)
      line({}, { usage: { input_tokens: 1, output_tokens: 99 } }), // final
      line({ timestamp: "2026-01-01T00:00:00Z" }, { id: "msg_old" }),
      "linha quebrada",
    ].join("\n"),
  );
  const all = await collect(root, null);
  assert.equal(all.length, 2);
  assert.equal(all.find((e) => e.messageId === "msg_1").outputTokens, 99);
  const recent = await collect(root, new Date("2026-09-01T00:00:00Z"));
  // filtro por data de modificação do arquivo (recém-criado) + por timestamp da entrada
  assert.deepEqual(recent.map((e) => e.messageId), ["msg_1"]);
});

test("send envia em lotes com X-API-Key e soma os resultados; erro HTTP interrompe", async () => {
  const seen = [];
  const server = createServer((req, res) => {
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", () => {
      const parsed = JSON.parse(body);
      seen.push({ key: req.headers["x-api-key"], n: parsed.entries.length, url: req.url });
      if (req.headers["x-api-key"] !== "k") {
        res.writeHead(401).end("{}");
        return;
      }
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ received: parsed.entries.length, inserted: parsed.entries.length - 1, duplicates: 1 }));
    });
  });
  await new Promise((r) => server.listen(0, r));
  const url = `http://127.0.0.1:${server.address().port}`;
  const entries = Array.from({ length: 1200 }, (_, i) => ({ messageId: `m${i}` }));
  const r = await send(entries, { url, apiKey: "k" });
  assert.deepEqual(seen.map((s) => s.n), [500, 500, 200]);
  assert.equal(seen[0].url, "/api/tokens/usage");
  assert.deepEqual(r, { received: 1200, inserted: 1197, duplicates: 3 });
  await assert.rejects(send(entries, { url, apiKey: "errada" }), /401/);
  await new Promise((r) => server.close(r));
});

test("execução única sai com código 1 quando o envio falha (e 0 no dry-run)", async () => {
  const root = await mkdtemp(join(tmpdir(), "tc-exit-"));
  await mkdir(join(root, "p"));
  await writeFile(join(root, "p", "a.jsonl"), line());
  const script = new URL("../collect.mjs", import.meta.url).pathname;
  const env = { ...process.env, TOKEN_CONTROL_URL: "http://127.0.0.1:1", TOKEN_CONTROL_API_KEY: "k" };
  const fail = spawnSync(process.execPath, [script, "--dir", root, "--days", "36500"], { env, encoding: "utf8" });
  assert.equal(fail.status, 1);
  assert.match(fail.stderr, /erro:/);
  const dry = spawnSync(process.execPath, [script, "--dir", root, "--days", "36500", "--dry-run"], { env, encoding: "utf8" });
  assert.equal(dry.status, 0);
  assert.match(dry.stdout, /1 mensagens/);
});
