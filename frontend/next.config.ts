import type { NextConfig } from "next";
import path from "path";

const isDev = process.env.NODE_ENV === "development";

// ============================================================
// Content Security Policy (CSP)
// Em desenvolvimento: permissivo para HMR, Turbopack e DevTools
// Em produção: restritivo — apenas origens necessárias
// ============================================================
const cspDirectives = isDev
  ? [
      // DEV: permissivo para HMR (Hot Module Replacement) do Next.js/Turbopack
      `default-src 'self'`,
      `script-src 'self' 'unsafe-eval' 'unsafe-inline'`,
      `style-src 'self' 'unsafe-inline' https://fonts.googleapis.com`,
      `font-src 'self' https://fonts.gstatic.com`,
      `img-src 'self' data: blob: https:`,
      `connect-src 'self' http://localhost:8080 ws://localhost:3000 wss://localhost:3000`,
      `worker-src 'self' blob:`,
      `frame-ancestors 'none'`,
    ]
  : [
      // PRODUÇÃO: restritivo e seguro
      `default-src 'self'`,
      `script-src 'self' 'unsafe-inline'`,
      `style-src 'self' 'unsafe-inline' https://fonts.googleapis.com`,
      `font-src 'self' https://fonts.gstatic.com`,
      `img-src 'self' data: blob: https:`,
      `connect-src 'self' ${process.env.NEXT_PUBLIC_API_URL || ''}`.trimEnd(),
      `worker-src 'self' blob:`,
      `frame-ancestors 'none'`,
      `upgrade-insecure-requests`,
    ];

const contentSecurityPolicy = cspDirectives.join("; ");

const securityHeaders = [
  // Corrige o bloqueio de chunks JS gerados pelo Turbopack/Next.js
  {
    key: "Content-Security-Policy",
    value: contentSecurityPolicy,
  },
  // Previne clickjacking
  {
    key: "X-Frame-Options",
    value: "DENY",
  },
  // Previne MIME type sniffing
  {
    key: "X-Content-Type-Options",
    value: "nosniff",
  },
  // Controla informações do referrer
  {
    key: "Referrer-Policy",
    value: "strict-origin-when-cross-origin",
  },
  // Permissões de funcionalidades do browser (sem câmera/microfone não autorizados)
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=()",
  },
];

const nextConfig: NextConfig = {
  outputFileTracingRoot: path.join(__dirname),

  // Cabeçalhos de segurança aplicados a todas as rotas
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: securityHeaders,
      },
    ];
  },

  // Turbopack (mantido conforme configuração original)
  turbopack: {
    root: path.join(__dirname),
  },
};

export default nextConfig;
