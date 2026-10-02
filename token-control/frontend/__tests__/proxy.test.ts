import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { config, proxy, safeEqual } from "@/proxy";

const basic = (u: string, p: string) => `Basic ${btoa(`${u}:${p}`)}`;
const req = (url: string, init?: { method?: string; auth?: string }) =>
  new NextRequest(url, { method: init?.method ?? "GET", headers: init?.auth ? { authorization: init.auth } : {} });

describe("proxy (basic auth do dashboard)", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("matcher cobre a raiz (regressão: com basePath a página abria sem login)", () => {
    expect(config.matcher).toContain("/");
  });

  it("safeEqual compara em tempo constante e rejeita tamanhos diferentes", () => {
    expect(safeEqual("abc", "abc")).toBe(true);
    expect(safeEqual("abc", "abd")).toBe(false);
    expect(safeEqual("abc", "abcd")).toBe(false);
  });

  it("produção sem senha configurada falha fechado (503)", () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("DASHBOARD_PASSWORD", "");
    expect(proxy(req("http://x/")).status).toBe(503);
  });

  it("sem credencial → 401 com WWW-Authenticate; credencial errada → 401", () => {
    vi.stubEnv("DASHBOARD_PASSWORD", "segredo");
    const r = proxy(req("http://x/"));
    expect(r.status).toBe(401);
    expect(r.headers.get("www-authenticate")).toContain("Basic");
    expect(proxy(req("http://x/", { auth: basic("admin", "errada") })).status).toBe(401);
    expect(proxy(req("http://x/", { auth: "Basic %%%" })).status).toBe(401);
  });

  it("credencial correta passa (usuário padrão admin)", () => {
    vi.stubEnv("DASHBOARD_PASSWORD", "segredo");
    const r = proxy(req("http://x/", { auth: basic("admin", "segredo") }));
    expect(r.status).toBe(200);
  });

  it("ingestão POST /api/tokens/usage não exige o login do dashboard", () => {
    vi.stubEnv("DASHBOARD_PASSWORD", "segredo");
    expect(proxy(req("http://x/api/tokens/usage", { method: "POST" })).status).toBe(200);
    // mas GET na mesma rota continua protegido
    expect(proxy(req("http://x/api/tokens/summary")).status).toBe(401);
  });
});
