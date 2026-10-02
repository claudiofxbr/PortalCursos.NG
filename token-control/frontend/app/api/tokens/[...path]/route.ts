import { isAllowed, usesClientKey } from "@/lib/bff";

export const dynamic = "force-dynamic";

const BACKEND = () => (process.env.BACKEND_URL || "http://localhost:8090").replace(/\/$/, "");

async function forward(req: Request, ctx: { params: Promise<{ path: string[] }> }): Promise<Response> {
  const { path } = await ctx.params;
  if (!isAllowed(req.method, path)) {
    return Response.json({ error: "not_found" }, { status: 404 });
  }

  const key = usesClientKey(req.method, path) ? req.headers.get("x-api-key") : process.env.TOKEN_CONTROL_API_KEY;
  if (!key) {
    return Response.json({ error: "unauthorized" }, { status: 401 });
  }

  const url = `${BACKEND()}/api/tokens/${path[0]}${new URL(req.url).search}`;
  const hasBody = req.method === "POST" || req.method === "PUT";
  try {
    const upstream = await fetch(url, {
      method: req.method,
      headers: { "X-API-Key": key, ...(hasBody ? { "Content-Type": "application/json" } : {}) },
      body: hasBody ? await req.text() : undefined,
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
    });
    return new Response(await upstream.text(), {
      status: upstream.status,
      headers: { "Content-Type": upstream.headers.get("content-type") ?? "application/json" },
    });
  } catch {
    return Response.json({ error: "backend_unavailable" }, { status: 502 });
  }
}

export { forward as GET, forward as POST, forward as PUT };
