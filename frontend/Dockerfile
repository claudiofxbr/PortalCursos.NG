FROM node:20-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

FROM node:20-alpine AS builder
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
FROM node:20-alpine AS runner
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
