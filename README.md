# PortalCursos.NG

Sistema de gestão acadêmica/administrativa (matrículas, financeiro, secretaria, manutenção predial etc.).

## Stack

- **Backend:** Java 17 + Spring Boot 3, Maven, PostgreSQL (Flyway para migrations).
- **Frontend:** Next.js (React 19) + TypeScript, em `frontend/`.
- **Deploy:** Docker Compose + Traefik em VPS Hostinger, orquestrado via GitHub Actions (`.github/workflows/`).

## Desenvolvimento local

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

Rodar os testes:

```bash
cd backend
./mvnw clean verify
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Acesse `http://localhost:3000`.

Build de produção: `npm run build` / `npm run start`. Lint: `npm run lint`.

## Deploy

Fluxo de deploy documentado em `devops/scripts/deploy_ci.sh` (acionado pelo CI) e `deploy-hostinger.ps1` (script local auxiliar). Confirme com o responsável pelo projeto qual é o ambiente de produção atual antes de rodar qualquer script de deploy.

## Torre de Controle dos Processos

Painel: https://claude.ai/code/artifact/5571a2a0-087b-4ff3-9cbe-15d76b05564e (doc `status/current`).

**O que é automático (sem intervenção manual):** o workflow [`torre-controle-status.yml`](.github/workflows/torre-controle-status.yml) roda a cada conclusão de qualquer outro workflow do repo (mais um fallback a cada 6h e disparo manual) e regrava `docs/torre-de-controle/status.json` **na branch `torre-controle-data`** (não em `main` — `main` é protegida, exige PR + status checks, e não aceita push direto de bot) com:
- **Inclusão automática de processo novo**: descobre os workflows ativos via `gh api .../actions/workflows` — um `.yml` novo aparece sozinho no próximo ciclo, sem editar nada aqui.
- **Atualização de registros existentes**: reconsulta o último run de cada workflow a cada disparo.
- **Notificação de encerramento**: o próprio evento `workflow_run: completed` é o gatilho — o status reflete sucesso/falha assim que o processo termina.
- **Fila de PRs**: descoberta pelo mesmo princípio via `gh pr list --state open`, com o resultado do CI de cada PR embutido no `detail`.
- **Exceção conhecida**: Dependabot não é um workflow do Actions (sem `run`/`conclusion` para observar) — fica como entrada fixa "ativo", documentada aqui em vez de inferida.
- **Tratamento de falha**: se a chamada à API do GitHub falhar, o job falha (visível em Actions) e o arquivo anterior é preservado — nunca sobrescreve com dado parcial/inconsistente; o próximo disparo (workflow seguinte, ou o fallback de 6h) tenta de novo.

**O que continua manual, e por quê:** publicar `status.json` no painel ao vivo (Claude Artifact) exige que uma sessão do Claude chame `write_db` — uma ação que, fora de uma sessão interativa, dispara um prompt de permissão que ninguém pode aprovar; a plataforma bloqueia deliberadamente qualquer configuração para pular esse prompt (barreira de segurança contra escrita de dados sem supervisão humana — mesma limitação já confirmada no projeto irmão CVFacil.NG). Por isso, o último passo continua sendo pedir ao Claude, em sessão interativa: *"atualiza a torre de controle"* — a partir de agora essa etapa só copia `docs/torre-de-controle/status.json` (já pronto e correto) para o banco do artifact, sem precisar recalcular nada manualmente.
