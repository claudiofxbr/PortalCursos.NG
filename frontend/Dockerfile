FROM node:26-alpine@sha256:e88a35be04478413b7c71c455cd9865de9b9360e1f43456be5951032d7ac1a66 AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

FROM node:26-alpine@sha256:e88a35be04478413b7c71c455cd9865de9b9360e1f43456be5951032d7ac1a66 AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_BASE_PATH
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_BASE_PATH=$NEXT_PUBLIC_BASE_PATH
RUN npm run build

# output: standalone (next.config.ts) gera .next/standalone com server.js
# autocontido — já inclui next.config.js + apenas os node_modules necessários
# em runtime, garantindo que basePath/assetPrefix sejam aplicados corretamente.
FROM node:26-alpine@sha256:e88a35be04478413b7c71c455cd9865de9b9360e1f43456be5951032d7ac1a66 AS runner
# apk upgrade: corrige CVE-2026-45447 (OpenSSL libcrypto3/libssl3) ainda nao
# incorporada na imagem base node:20-alpine mais recente disponivel.
RUN apk upgrade --no-cache libcrypto3 libssl3
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/public ./public
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static
USER node
EXPOSE 3000
ENV PORT=3000
# server.js do standalone só escuta em localhost por padrão;
# 0.0.0.0 é obrigatório para aceitar conexões de fora do container.
ENV HOSTNAME="0.0.0.0"
CMD ["node", "server.js"]
