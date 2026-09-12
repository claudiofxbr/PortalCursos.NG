# CLAUDE.md — PortalCursos.NG (raiz do projeto)

Este arquivo complementa o `CLAUDE.md` global do usuário (`~/.claude/CLAUDE.md`), que já se aplica automaticamente. Aqui só entram particularidades deste projeto.

> Nota: existe também um `frontend/CLAUDE.md` e `frontend/AGENTS.md` específicos da pasta frontend — quando trabalhar dentro de `frontend/`, aquele arquivo tem prioridade sobre este para o que for específico do frontend.

## Ambiente de produção

- **Produção = VPS Hostinger** (`xavierbr-vps.tech`, `root@69.62.87.38`). O `render.yaml` existe mas **não** é o ambiente ativo.
- A VPS é **compartilhada** com o CVFacil.NG (containers `cvfacil-*`, site nginx `cvfacil.xavierbr-vps.tech`, backend interno `127.0.0.1:7777`). Nunca mexer nos recursos do CVFacil.NG (Regra Crítica #1).
- Box de 8 GB com ~15 containers de vários projetos → **swap de 3 GB** configurado (`/swapfile`, `vm.swappiness=10`). Pouca folga de RAM; cuidado ao subir serviços novos.
- **Edge = nginx do host** (`/etc/nginx/sites-available/portalcursos`), NÃO o Traefik. As labels Traefik nos containers existem só para descoberta pelo EasyPanel. O nginx faz `proxy_pass` para os upstreams `portalcursos_api` / `portalcursos_web`, definidos em `/etc/nginx/conf.d/portalcursos-upstream.conf`.
- **Banco = Neon exclusivamente** (`SPRING_DATASOURCE_URL` → `ep-*.sa-east-1.aws.neon.tech`). Não há PostgreSQL local; qualquer container/serviço `*postgres*` na VPS é lixo e pode ser removido (inclusive services Docker Swarm — `docker rm` não basta, usar `docker service rm`).

## Deploy (`.github/workflows/deploy.yml` → `devops/scripts/deploy_ci.sh`)

- Roda na VPS via SSH, só em `push` para `main` (o `deploy` job tem `if: github.event_name == 'push'` — `workflow_dispatch` não deploya).
- **Toggle `DEPLOY_STRATEGY` no `.env` da VPS:**
  - `recreate` — recria os containers no lugar; janela de ~15-20s de 502.
  - `blue-green` (**ativo em produção**) — sobe a cor ociosa (`backend-green`/`frontend-green`, portas host 8091/3011), valida o health, troca o upstream do nginx com `nginx -s reload` graceful, só então para a cor antiga. Zero downtime (verificado: 0 respostas 5xx no `access.log` durante a troca). Rollback = não trocar o upstream; `nginx -t` gateia o switch.
- `docker-compose.prod.yml` usa anchors `x-backend`/`x-frontend`; os serviços `-green` só sobem em modo blue-green.
- Ao mudar `nginx.conf` ou `portalcursos-upstream.conf`: re-aplicar na VPS com `bash devops/scripts/apply_nginx.sh`.
- Scripts manuais na raiz (`.ps1`/`.bat`) e `devops/scripts/deploy_*.sh` alternativos — só `deploy_ci.sh` está no caminho do CI; confirme com o usuário antes de rodar os outros.

## Banco de dados / modelo

- **Soft-delete** (`@SQLDelete` + coluna `active`):
  - `@SQLRestriction("active = true")` apenas em **`Student`, `Payment`, `RepairTicket`** (entidades transacionais).
  - `Course` e `StaffMember` **não** têm `@SQLRestriction` (são referência, apontadas por FKs de registros históricos) — as listagens de catálogo/staff filtram `active = true` explicitamente nas `@Query` (`CourseRepository.findAllActiveWithCreator`, `StaffMemberRepository.findAllByActiveTrue`, etc.). Usar métodos com filtro explícito, não `findAll()`/`findById()` cru.
  - `Payment.student` tem `@NotFound(action = IGNORE)` (Student é soft-deleted de verdade) → é LAZY; as queries de listagem do `PaymentRepository` fazem `LEFT JOIN FETCH p.student` para evitar N+1.
  - Fluxos que precisam ver inativos (LGPD, reativação de colaborador) usam métodos `*IncludingInactive` (nativeQuery).
- `UNIQUE` de `students.cpf`/`students.email` é **parcial** (`WHERE active = true`, V21) — permite reusar CPF/e-mail de aluno desativado.
- Migrações Flyway em `backend/src/main/resources/db/migration/`. `spring.flyway.enabled=true` em produção; **desabilitado nos testes** (H2, `src/test/resources/application.properties`) para a maioria da suíte rodar rápido sem Docker.
- `backend/src/test/java/com/portalcursos/ng02/PostgresMigrationIntegrationTest.java` roda as migrations Flyway reais contra um Postgres via Testcontainers (não H2) — cobre migrations V1..V21 aplicando sem falha, `@SQLRestriction` (soft-delete) e o índice único parcial de `students.cpf/email` (V21) no dialeto Postgres de verdade. `DatabaseConfig` declara o `DataSource` manualmente a partir de `DataSourceProperties` (trata URLs estilo Render/Neon) — não usa `JdbcConnectionDetails`, então esse teste publica as propriedades do container via `@DynamicPropertySource` clássico, não `@ServiceConnection`. Exige Docker rodando localmente (CI/GitHub Actions `ubuntu-latest` já tem por padrão).
- `/api/health` reporta o estado do Flyway em `diagnostics`: `migrations` (OK/FAILED/UNKNOWN), `migrations_latest`, `migrations_failed`. Falha silenciosa — não altera status nem HTTP code.
- Scripts SQL de correção manual (fora do Flyway) ficam em `legacy-sql-do-not-run/` — não rodar contra produção sem confirmação explícita.

## Código / arquitetura

- Tratamento de exceções centralizado em `backend/src/main/java/.../exception/` (`GlobalExceptionHandler`, `ResourceNotFoundException`, `BusinessException`). Usar esse padrão em endpoints novos; param inválido de path/enum deve virar `BusinessException` (400), não cair no handler genérico (500).
- Logs de autenticação (`AuthController`, `AuthTokenFilter`) **não devem logar PII crua** — usar `maskUsername()`; não logar fragmento de token; IP só em evento de bloqueio por força bruta.
- Uploads de documentos de usuários em `backend/uploads/` — nunca commitar nem expor (dados pessoais).
- JDK 21 / Node 24 (LTS) — Dependabot ignora o major do `typescript` (`.github/dependabot.yml`); TS 7 está bloqueado por falta de API programática que o `typescript-eslint` precisa.
- CI com jobs paralelizados (`test-backend`/`test-frontend`, `security-scan-*`). A branch protection `required_status_checks.contexts` precisa bater exatamente com os nomes dos jobs — renomear job = atualizar a proteção via `gh api` senão os PRs ficam `BLOCKED`.
- Frontend tem suíte de testes real (Vitest + React Testing Library, `frontend/__tests__/`) cobrindo os fluxos críticos: login (`auth/signin`) e matrícula com upload de documentos (`academic/enroll`). Roda como parte do job `test-frontend` no CI (`npm run test:run`, antes do build). `npm test` local roda em modo watch.
- Lote E (auditoria) — quebra de `AuthController`/`FinancialController` em Services, item a item, com aval prévio para cada um (achado marcado como "split arriscado"):
  - **A1 (feito)**: lógica de `signin` extraída para `AuthService.login` — controller só resolve IP, loga e traduz o resultado/exceções (`AccountLockedException` → 423, `AuthenticationException` → 401) em `ResponseEntity`.
  - **A2 (feito)**: lógica de `refreshtoken` extraída para `AuthService.refreshToken` (lookup de sessão, checagem de expiração, rotação de token). O bloqueio por força bruta e a extração do token do cookie/body continuam no controller — são checagens de entrada da requisição, não lógica de domínio da sessão. `SessionExpiredException`/`InvalidSessionException` novas ficam fora do `GlobalExceptionHandler` de propósito, para preservar o corpo de resposta legado (`{"message": "..."}`, sem os campos timestamp/status/error/path).
  - **A3 (feito)**: lógica de `signup` extraída para `AuthService.signup` (checagem de role privilegiada, unicidade de username/e-mail, criação do usuário). Bloqueio por força bruta e contagem de tentativa continuam no controller. `SignupPrivilegeException` (403) e `SignupConflictException` (400) novas ficam fora do `GlobalExceptionHandler` pelo mesmo motivo das anteriores (preservar `{"message": "..."}` legado); role desconhecida continua virando `BusinessException` (400 padrão do handler, sem mudança).
  - `AuthController` agora só tem: `extractClientIp`/`maskUsername` (helpers de request/log), `/me` (lê `SecurityContextHolder` direto, nunca teve lógica de negócio para extrair) e `/signout` (thin o suficiente — não precisou de item próprio). Todo o Lote E do `AuthController` está concluído.
  - **F1 (feito)**: `hasElevatedPrivileges`/`ownsStudentRecord`/`ownsPayment` movidos de `FinancialController` para `PaymentAuthorizationService` (`authorizationService.*`). Puro código movido — mesma lógica, mesmas assinaturas, sem exceção nova (não precisa: são checagens booleanas, não fluxo de erro).
  - **F2 (feito)**: `createManualCharge`/`updateCharge`/`deleteCharge` movidos de `FinancialController` para `PaymentService` (`paymentService.*`). `ManualChargeRequest` deixou de ser classe aninhada do controller e virou DTO próprio (`dto/ManualChargeRequest.java`), já que agora é consumido pelo service também. Puro código movido — mesma lógica, mesmas mensagens de erro (404 "Cobrança não encontrada" / "Estudante de graduação|pós-graduação não encontrado").
  - **F3 (feito)**: `generatePix`/`generateBoleto` movidos para `PaymentCodeGenerationService` (`paymentCodeGenerationService.*`). Busca da fatura (404) e checagem de ownership (403) continuam no controller — mesmo critério de F1/F2. **Bug real encontrado e corrigido durante a extração**: `String.format("%08s", ...)` é inválido em Java (flag `0` não existe para conversão `%s`) e fazia `generatePix` lançar `FormatFlagsConversionMismatchException` (500) sempre que chamado — nunca coberto por teste antes disso. Corrigido para `"%8s"` + `.replace(" ", "0")`, que já era o padrão usado (o replace só fazia sentido com espaço, não com o zero-padding que `%08s` tentava e falhava em aplicar).
  - **Lote E concluído**: `AuthController` (A1/A2/A3) e `FinancialController` (F1/F2/F3) totalmente migrados para Services. Próxima quebra desse tipo, se houver, é um novo achado — não sobra nada pendente deste backlog.

## Torre de Controle dos Processos

- Painel ao vivo dos processos do PortalCursos.NG: **https://claude.ai/code/artifact/5571a2a0-087b-4ff3-9cbe-15d76b05564e** (artifact; doc `status/current` no banco do artifact, via `ArtifactData`).
- **Só processos do PortalCursos.NG.** Processos automáticos/recorrentes (Dependabot, CI/CD, blue-green, etc.) ficam sempre visíveis; itens que concluem com sucesso são **removidos** da lista.
- Ao criar um processo automático novo (workflow, rotina, monitor), registrá-lo no painel no mesmo passo.
- Escrever sempre a lista `processes` completa via `ArtifactData` action `set` — nunca `update` parcial (não faz merge dentro de arrays).
- Quando a própria Torre está operacional (writes no `status/current` funcionando) **e** o PortalCursos.NG está saudável (`/api/health` = `UP`, Neon `CONNECTED`, `migrations` OK), registrar essa confirmação no painel.
