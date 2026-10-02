import type { NextConfig } from "next";
import path from "path";

const BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH || "";
const isDev = process.env.NODE_ENV === "development";

// connect-src 'self': o browser só fala com o próprio Next (BFF); a chave do backend nunca chega ao cliente.
const csp = [
  `default-src 'self'`,
  isDev ? `script-src 'self' 'unsafe-eval' 'unsafe-inline'` : `script-src 'self' 'unsafe-inline'`,
  `style-src 'self' 'unsafe-inline'`,
  `img-src 'self' data:`,
  isDev ? `connect-src 'self' ws://localhost:3010` : `connect-src 'self'`,
  `frame-ancestors 'none'`,
].join("; ");

const nextConfig: NextConfig = {
  outputFileTracingRoot: path.join(__dirname),
  output: "standalone",
  basePath: BASE_PATH,
  assetPrefix: BASE_PATH || undefined,
  poweredByHeader: false,
  turbopack: { root: path.join(__dirname) },
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
          { key: "Cache-Control", value: "no-store" },
        ],
      },
    ];
  },
};

export default nextConfig;
