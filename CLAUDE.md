# CLAUDE.md — PortalCursos.NG (raiz do projeto)

Este arquivo complementa o `CLAUDE.md` global do usuário (`~/.claude/CLAUDE.md`), que já se aplica automaticamente. Aqui só entram particularidades deste projeto.

> Nota: existe também um `frontend/CLAUDE.md` e `frontend/AGENTS.md` específicos da pasta frontend — quando trabalhar dentro de `frontend/`, aquele arquivo tem prioridade sobre este para o que for específico do frontend.

## Stack detectada
- **Backend:** Java + Spring Boot, pacote `com.portalcursos.ng02` (`backend/src/main/java/...`), autenticação JWT (`AuthEntryPointJwt`, `JwtResponse`, `UserDetailsServiceImpl`), Maven (`mvnw`).
- **Frontend:** Next.js + TypeScript (`frontend/app/`).
- Deploy configurado via `render.yaml` (Render.com) — além do padrão global de VPS Hostinger, confirme com o usuário qual ambiente é o de produção atual antes de sugerir mudanças de deploy.
- SQL de manutenção/setup em `backend/src/main/resources/*.sql` (`setup-database.sql`, `setup-indexes.sql`, `robust-maintenance.sql`, `SUPREME-DATABASE-FINAL-FIX.sql`) e na raiz (`FIX-ROOT-ACCESS.sql`, `SUPREME-DATABASE-FIX.sql`, `SUPREME-DATABASE-V31.4-FINAL.sql`, `SUPREME-DATABASE-V31.6-ULTRA.sql`) — parecem scripts de correção manual acumulados; não rode contra produção sem confirmação explícita.

## Particularidades
- Uploads de documentos de usuários ficam versionados em `backend/uploads/` — nunca commitar nem expor esses arquivos, contêm dados pessoais (fotos, documentos).
- Muitos scripts de deploy/controle na raiz (`push-to-github.ps1/.bat`, `GO.ps1/.bat`, `Oi.ps1/.bat`, `supreme-control.ps1/.bat`, `deploy-hostinger.ps1`, `deploy-vps.ps1`, `manage-vps.ps1`, `setup-and-start.ps1`, `start-portal.ps1/.bat`, `update-security.ps1/.bat`, `verify-deploy.ps1`) — confirme com o usuário qual é o fluxo de deploy/manutenção atual antes de executar qualquer um.
- Tratamento de exceções centralizado em `backend/src/main/java/.../exception/` (`GlobalExceptionHandler`, `ResourceNotFoundException`) — usar esse padrão ao adicionar novos endpoints em vez de tratar erros ad-hoc nos controllers.
