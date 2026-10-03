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

## Controle do banco Neon
Skills oficiais instaladas em `.claude/skills/` (`neon`, `neon-postgres`, via `npx neon@latest skills -s neon -s neon-postgres -y`; `skills-lock.json` fixa as versões). Aplicado ao app:
- **Pooled × direto**: a aplicação usa a URL `-pooler`; o Flyway usa `NEON_DIRECT_URL` (conexão direta, recomendada pelo Neon para migrations). Sem `NEON_DIRECT_URL`, usa a mesma URL.
- **Histórico Flyway próprio** (`token_control_schema_history`, `baseline-version=0`): o app convive com um banco/branch Neon que seja clone do PortalCursos.NG (schema não-vazio e `flyway_schema_history` alheio) sem colidir nem pular a V1. Testado contra Postgres com histórico "v22" de outro app.
- **`GET /api/tokens/db`** (card "Banco de dados Neon"): conectividade e latência, versão do Postgres, tamanho do banco, conexões (total/ativas/máx), estado do Flyway (`OK/FAILED/UNKNOWN`), linhas e tamanho das tabelas do app, e endpoint/região/pooled extraídos da URL **sem credenciais**. Banco fora do ar → `connected:false` (200), nunca 500. Aviso na tela se estiver em conexão direta.
- **API de gestão do Neon (opcional, somente leitura)**: com `NEON_API_KEY` + `NEON_PROJECT_ID`, mostra consumo do período (computação, dados escritos, transferência), branches e computes. Cache de 60 s; erro vira `HTTP <status>` sem vazar a chave. ⚠️ **Não verificado contra a API real** (a documentação/API do Neon não são acessíveis do ambiente de desenvolvimento): os campos são lidos de forma tolerante (ausente → `—`), e há teste com servidor simulado. Validar uma vez com a chave real.
- Operações que alteram o Neon (branch, restore, apagar) ficam **fora** do app: são manuais ou via CLI `neon`, com confirmação.

## Torre de Controle dos Processos
Aba própria + selo no cabeçalho; especificação completa em `docs/TORRE.md`. Calculada sob demanda a partir de `SummaryService`, `DbStatusService`, `ConfigService` e `UsageRepository` (sem tabelas novas).

## Aba "Mês atual" (além do ciclo semanal)
- Migration `V2`: `token_plan_config.monthly_limit_tokens` (opcional, `CHECK > 0`). **Sem valor, o limite do mês é estimado = limite semanal × dias do mês ÷ 7**, e a tela sinaliza "referência estimada". O orçamento mensal real pode ser definido em "Configurar plano".
- Mês civil no fuso configurado `[dia 1 00:00, dia 1 do mês seguinte)`, com aritmética em data local (virada de mês e DST testadas).
- O frontend usa um único `PeriodPanel` para as duas abas (semana e mês): mesma tela, só mudam janela, rótulos e limite. O histórico de ciclos aparece só na aba semanal.

## Desempenho da ingestão com latência até o Neon
Achado em produção (2026-10-02): o primeiro envio real (lotes de 500) deu **502**. Causa: gravação com `saveAll` + `IDENTITY` = **um INSERT por mensagem** (o Hibernate desliga o batch com IDENTITY); com ~120 ms de latência VPS→Neon, 500 mensagens levavam **~62 s** e estouravam o timeout do BFF (15 s). O Postgres local dos testes não tem latência e escondia o problema. Correção: `JdbcTemplate.batchUpdate` numa transação + `reWriteBatchedInserts=true` (multi-row, 1 round-trip) → **~0,8 s** para 500 mensagens com a mesma latência simulada (proxy TCP com +60 ms/direção); timeout do BFF subiu para 60 s por segurança. Idempotência mantida (checagem prévia + retry em violação de unicidade).

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
| GET | `/api/tokens/report` | **Relatório de análise** em português (6 seções com texto, marcadores e tabela) — ver `RELATORIO.md`. |
| GET | `/api/tokens/tower` | **Torre de Controle dos Processos** (ver `TORRE.md`): estado geral + itens por categoria (work/queue/health/auto). |
| GET | `/api/tokens/month` | **Mês atual** (dia 1 até hoje, fuso configurado): uso, restante, projeção até o fim do mês, diário (1 item por dia civil), por processo/modelo. `limitEstimated=true` quando não há orçamento mensal. |
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
| Unitário + integração backend (H2/Flyway) | `cd backend && mvn -B verify` | 54 testes ✅ (inclui sonda Postgres real, V2, mês atual, Torre e relatório) |
| Sonda do banco + migration V1 em Postgres 16 real | `TC_TEST_PG_URL=… mvn verify` (`PostgresDbProbeTest`; no CI via service postgres) | ✅ (Testcontainers não disponível: sem Docker no ambiente) |
| Collector | `cd collector && npm test` | 5 testes ✅; ingestão real de 31 msgs, reenvio = 31 duplicadas ✅ |
| Frontend | `npm run lint && npm run test:run && npm run build` | lint ✅, 34 testes ✅, build ✅ |
| E2E navegador (Chromium) | `e2e/dashboard.e2e.mjs` | login, KPIs, 11 SVGs, salvar config, sem erros de console ✅ |
| Carga/estresse | `load-test/load.mjs` | ver abaixo |

**Carga** (Postgres local, heap 256 MB, pool 3, mix 50% ingestão de 50 msgs/50% leitura): conc=5 → p95 70 ms; conc=20 → p95 495 ms; **conc=50 → p95 ~1,7 s** (acima do limite de 800 ms; 0% de erro em todos os estágios). O uso real é 1 usuário + 1 collector, então o critério de aceite é conc ≤ 20; 50 é teste de estresse informativo. Para escalar: índice cobrindo `(occurred_at, process, model)` ou pré-agregação por hora.

## Fora do escopo desta entrega / pendências
- **Deploy na VPS não executado** — é ato de produção em VPS compartilhada e exige confirmação explícita (ver `DEPLOY.md`).
- A Torre do CVFacil.NG não é acessível a esta sessão (escopo do repositório); o painel segue o padrão da Torre do PortalCursos.NG.
