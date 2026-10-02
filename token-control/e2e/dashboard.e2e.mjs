// E2E real no navegador: dashboard → login Basic → cards/gráficos → salvar configuração.
// Pré-requisito: stack no ar (backend + frontend) com dados. Rodar com Playwright instalado:
//   E2E_URL=http://127.0.0.1:3010/tokencontrol E2E_USER=admin E2E_PASS=segredo node dashboard.e2e.mjs
import { chromium } from "playwright";
import assert from "node:assert/strict";

const URL_ = process.env.E2E_URL || "http://127.0.0.1:3010/tokencontrol";
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
const ctx = await browser.newContext({
  httpCredentials: { username: process.env.E2E_USER || "admin", password: process.env.E2E_PASS || "segredo" },
  viewport: { width: 1280, height: 1600 },
});
const page = await ctx.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(e.message));
page.on("console", (m) => m.type() === "error" && errors.push(m.text()));

// sem credencial → 401
const anon = await browser.newContext();
const r = await (await anon.newPage()).goto(URL_ + "/");
assert.equal(r.status(), 401, "dashboard deve exigir login");
await anon.close();

await page.goto(URL_ + "/");
await page.getByText("Uso do ciclo atual").waitFor();
await page.getByRole("progressbar").waitFor();
for (const t of ["Próximo reset", "Últimas 5 horas", "Projeção até o reset", "Consumo por dia do ciclo", "Consumo por processo", "Consumo por modelo", "Histórico dos últimos ciclos"]) {
  await page.getByText(t, { exact: false }).first().waitFor();
}
const neon = page.getByRole("region", { name: "Banco de dados Neon" });
await neon.getByText("Conectado").waitFor();
await neon.getByText("OK · v1").waitFor();
await neon.getByRole("table", { name: "Tabelas do app" }).waitFor();
await page.waitForSelector("svg.recharts-surface");
const charts = await page.locator("svg.recharts-surface").count();
assert.ok(charts >= 5, `esperava ≥5 gráficos SVG, achei ${charts}`);

// configuração: alterar orçamento e ver refletido
await page.getByRole("button", { name: "Configurar plano" }).click();
await page.getByLabel("Orçamento semanal (tokens)").fill("40000000");
await page.getByRole("button", { name: "Salvar" }).click();
await page.getByText(/de 40 mi/).waitFor();
// restaura
await page.getByRole("button", { name: "Configurar plano" }).click();
await page.getByLabel("Orçamento semanal (tokens)").fill("50000000");
await page.getByRole("button", { name: "Salvar" }).click();
await page.getByText(/de 50 mi/).waitFor();

if (process.env.E2E_SCREENSHOT) await page.screenshot({ path: process.env.E2E_SCREENSHOT, fullPage: true });
assert.deepEqual(errors, [], "sem erros no console do navegador");
await browser.close();
console.log(`E2E OK (${charts} gráficos renderizados)`);
