import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/** Comparação em tempo constante (sem early-exit) para a credencial Basic. */
export function safeEqual(a: string, b: string): boolean {
  const len = Math.max(a.length, b.length);
  let diff = a.length ^ b.length;
  for (let i = 0; i < len; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}

export function proxy(request: NextRequest) {
  // Ingestão do collector tem a própria chave (validada no backend) — fica fora do login do dashboard.
  if (request.method === "POST" && request.nextUrl.pathname.endsWith("/api/tokens/usage")) {
    return NextResponse.next();
  }

  const password = process.env.DASHBOARD_PASSWORD;
  if (!password) {
    // fail-closed: sem senha configurada o dashboard não abre (exceto em dev local)
    if (process.env.NODE_ENV !== "production") return NextResponse.next();
    return new NextResponse("DASHBOARD_PASSWORD não configurada", { status: 503 });
  }

  const user = process.env.DASHBOARD_USER || "admin";
  const header = request.headers.get("authorization") ?? "";
  if (header.startsWith("Basic ")) {
    try {
      const decoded = atob(header.slice(6));
      const idx = decoded.indexOf(":");
      if (idx >= 0 && safeEqual(decoded.slice(0, idx), user) && safeEqual(decoded.slice(idx + 1), password)) {
        return NextResponse.next();
      }
    } catch {
      /* base64 inválido cai no 401 */
    }
  }
  return new NextResponse("Autenticação necessária", {
    status: 401,
    headers: { "WWW-Authenticate": 'Basic realm="Controle de Tokens", charset="UTF-8"' },
  });
}

export const config = {
  // "/" explícito: com basePath, a raiz não casa no padrão genérico e a página ficaria sem login.
  matcher: ["/", "/((?!_next/static|_next/image|favicon.ico).*)"],
};
