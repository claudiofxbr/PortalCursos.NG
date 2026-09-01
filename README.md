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
