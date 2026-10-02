import { afterEach, describe, expect, it, vi } from "vitest";
import { isAllowed, usesClientKey } from "@/lib/bff";
import { GET, POST, PUT } from "@/app/api/tokens/[...path]/route";

const ctx = (...path: string[]) => ({ params: Promise.resolve({ path }) });

describe("allowlist do BFF", () => {
  it("permite só as rotas usadas", () => {
    expect(isAllowed("GET", ["summary"])).toBe(true);
    expect(isAllowed("PUT", ["config"])).toBe(true);
    expect(isAllowed("POST", ["usage"])).toBe(true);
    expect(isAllowed("DELETE", ["summary"])).toBe(false);
    expect(isAllowed("GET", ["usage"])).toBe(false);
    expect(isAllowed("GET", ["summary", "x"])).toBe(false);
    expect(isAllowed("GET", ["..", "actuator"])).toBe(false);
  });
  it("só a ingestão usa a chave do cliente", () => {
    expect(usesClientKey("POST", ["usage"])).toBe(true);
    expect(usesClientKey("GET", ["summary"])).toBe(false);
  });
});

describe("route handler do BFF", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("GET injeta a chave do servidor e repassa query/status", async () => {
    vi.stubEnv("TOKEN_CONTROL_API_KEY", "server-key");
    vi.stubEnv("BACKEND_URL", "http://backend:8090/");
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"ok":1}', { status: 200, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const res = await GET(new Request("http://x/api/tokens/history?cycles=3"), ctx("history"));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: 1 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://backend:8090/api/tokens/history?cycles=3");
    expect(init.headers["X-API-Key"]).toBe("server-key");
  });

  it("rota fora da allowlist vira 404 sem chamar o backend", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const res = await GET(new Request("http://x/api/tokens/actuator"), ctx("actuator"));
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("POST usage sem chave do cliente é 401 e nunca usa a chave do servidor", async () => {
    vi.stubEnv("TOKEN_CONTROL_API_KEY", "server-key");
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const res = await POST(new Request("http://x/api/tokens/usage", { method: "POST", body: "{}" }), ctx("usage"));
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("POST usage repassa a chave do cliente e o corpo", async () => {
    vi.stubEnv("TOKEN_CONTROL_API_KEY", "server-key");
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await POST(new Request("http://x/api/tokens/usage", { method: "POST", body: '{"entries":[]}', headers: { "x-api-key": "client-key" } }), ctx("usage"));
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["X-API-Key"]).toBe("client-key");
    expect(init.body).toBe('{"entries":[]}');
  });

  it("backend fora do ar vira 502", async () => {
    vi.stubEnv("TOKEN_CONTROL_API_KEY", "k");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("ECONNREFUSED")));
    const res = await PUT(new Request("http://x/api/tokens/config", { method: "PUT", body: "{}" }), ctx("config"));
    expect(res.status).toBe(502);
  });
});
