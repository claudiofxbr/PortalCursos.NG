# Controle de Tokens Claude Code — Arquitetura

App isolado em `token-control/` (nenhum código do PortalCursos.NG foi alterado). Mostra o consumo de tokens do **Claude Code Pro** do último reset até o próximo, o uso semanal, a projeção e o histórico dos ciclos, com gráficos por dia, processo e modelo.

## Visão geral

```
~/.claude/projects/**/*.jsonl ──► collector (Node, JS) ──POST /api/tokens/usage──┐
                                                                                  ▼
Browser ──Basic auth──► Next.js (BFF + dashboard, recharts) ──X-API-Key──► Spring Boot (Java 21) ──► Neon Postgres
```

| Camada | Tecnologia | Responsabilidade |
|---|---|---|
| `collector/` | Node (JavaScript) | Lê os transcritos do Claude Code, extrai **somente** `message.usage` + metadados, envia em lotes de 500. Idempotente. |
| `backend/` | Java 21, Spring Boot 4.1, JPA, Flyway | Ingestão, cálculo do ciclo semanal, agregações, configuração. |
| `frontend/` | Next.js 16, React 19, recharts | Dashboard + BFF (a chave do backend nunca vai ao navegador) + login Basic (`proxy.ts`). |
| Banco | Neon (Postgres) | `token_usage_entries`, `token_plan_config`. Use um banco/branch **dedicado**. |
| Infra | Docker Compose, nginx do host | Stack própria, portas só em `127.0.0.1`. |

## Modelo de dados (migration `V1`)
- `token_usage_entries`: uma linha por resposta do assistente. `message_id` **UNIQUE** (idempotência), `hour_bucket` (hora UTC, p/ agregação barata), índices em `occurred_at` e `(process, occurred_at)`, CHECK de não-negativos. Sem conteúdo de conversa (sem PII).
- `token_plan_config`: linha única (`CHECK id=1`): dia/hora/fuso do reset, orçamento semanal, flag `count_cache_reads`.

## Regras de negócio
- **Ciclo** = `[último reset, próximo reset)` no fuso configurado; aritmética em data local (seguro contra DST); o instante exato do reset pertence ao novo ciclo.
- **Tokens contados** = input + output + cache_creation (+ cache_read se `count_cache_reads`). Leituras de cache ficam fora por padrão (são baratas e inflariam o número).
- **Orçamento semanal é configurável**: a Anthropic não publica o limite exato do Pro. O valor inicial (50 mi) é *placeholder* — ajuste conforme o `/usage` do Claude Code.
- **Projeção** = ritmo médio do ciclo extrapolado até o reset; só após 1 h de ciclo. Informa horário estimado de esgotamento.
- **Processo** = diretório do projeto (`cwd`); sub-agentes aparecem como `<projeto> · subagente`. Top 12 + "Outros".
- Dias do ciclo alinhados ao horário do reset (dia 1 começa no reset).

## Contrato da API (`X-API-Key` obrigatório em `/api/**`)
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/tokens/usage` | `{entries:[≤1000]}` → `{received, inserted, duplicates}`. 400 se inválido. |
| GET | `/api/tokens/summary` | Ciclo, uso, %, projeção, últimas 5 h, diário, por processo/modelo. |
| GET | `/api/tokens/history?cycles=8` | Uso dos últimos ciclos (1–26). |
| GET/PUT | `/api/tokens/config` | Configuração do plano (valida fuso e `HH:mm`). |
| GET | `/actuator/health` | Público (healthcheck do Docker). |

## Segurança
- Backend **não sobe** sem `TOKEN_CONTROL_API_KEY`; comparação em tempo constante.
- BFF com *allowlist* de rotas; POST `/usage` usa a chave do próprio collector, o restante usa a do servidor.
- Dashboard: HTTP Basic (`DASHBOARD_PASSWORD`), **fail-closed** em produção (503 sem senha). Achado corrigido no E2E: com `basePath` a raiz não casava no `matcher` do proxy e abria sem login — agora coberto por teste e E2E.
- CSP restritiva, `X-Frame-Options: DENY`, `no-store`; erros do backend não vazam stacktrace; segredos só por `.env` (nunca versionado).

## Verificação executada
| Camada | Comando | Resultado |
|---|---|---|
| Unitário + integração backend (H2/Flyway) | `cd backend && mvn -B verify` | 15 testes ✅ |
| Migration em Postgres 16 real | backend contra Postgres local | V1 aplicada ✅ (Testcontainers não disponível: sem Docker no ambiente) |
| Collector | `cd collector && npm test` | 5 testes ✅; ingestão real de 31 msgs, reenvio = 31 duplicadas ✅ |
| Frontend | `npm run lint && npm run test:run && npm run build` | lint ✅, 20 testes ✅, build ✅ |
| E2E navegador (Chromium) | `e2e/dashboard.e2e.mjs` | login, KPIs, 11 SVGs, salvar config, sem erros de console ✅ |
| Carga/estresse | `load-test/load.mjs` | ver abaixo |

**Carga** (Postgres local, heap 256 MB, pool 3, mix 50% ingestão de 50 msgs/50% leitura): conc=5 → p95 70 ms; conc=20 → p95 495 ms; **conc=50 → p95 ~1,7 s** (acima do limite de 800 ms; 0% de erro em todos os estágios). O uso real é 1 usuário + 1 collector, então o critério de aceite é conc ≤ 20; 50 é teste de estresse informativo. Para escalar: índice cobrindo `(occurred_at, process, model)` ou pré-agregação por hora.

## Fora do escopo desta entrega / pendências
- **Deploy na VPS não executado** — é ato de produção em VPS compartilhada e exige confirmação explícita (ver `DEPLOY.md`).
- A Torre do CVFacil.NG não é acessível a esta sessão (escopo do repositório); o painel segue o padrão da Torre do PortalCursos.NG.
