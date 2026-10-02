# Controle de Tokens Claude Code

Consumo de tokens do Claude Code Pro por semana e até o próximo reset, com gráficos. Veja `docs/ARQUITETURA.md` (arquitetura, API, testes) e `docs/DEPLOY.md`.

```bash
# backend (precisa de Postgres/Neon e das variáveis do .env.example)
cd backend && mvn -B verify
# frontend
cd frontend && npm ci && npm run lint && npm run test:run && npm run dev
# collector (envia o uso do Claude Code)
cd collector && npm test && node collect.mjs --dry-run
# carga
TOKEN_CONTROL_API_KEY=... node load-test/load.mjs --stages 5,20
```
