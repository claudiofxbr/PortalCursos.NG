# CLAUDE.md — PortalCursos.NG (raiz do projeto)

Este arquivo complementa o `CLAUDE.md` global do usuário (`~/.claude/CLAUDE.md`), que já se aplica automaticamente. Aqui só entram particularidades deste projeto.

> Nota: existe também um `frontend/CLAUDE.md` e `frontend/AGENTS.md` específicos da pasta frontend — quando trabalhar dentro de `frontend/`, aquele arquivo tem prioridade sobre este para o que for específico do frontend.

## Particularidades
- Deploy configurado via `render.yaml` (Render.com) — além do padrão global de VPS Hostinger, confirme com o usuário qual ambiente é o de produção atual antes de sugerir mudanças de deploy.
- Scripts SQL de correção manual (fora do Flyway) ficam quarentenados em `legacy-sql-do-not-run/` — não rode contra produção sem confirmação explícita (ver README nessa pasta).
- Uploads de documentos de usuários ficam versionados em `backend/uploads/` — nunca commitar nem expor esses arquivos, contêm dados pessoais (fotos, documentos).
- Vários scripts de deploy/controle na raiz (`.ps1`/`.bat`) — confirme com o usuário qual é o fluxo de deploy/manutenção atual antes de executar qualquer um.
- Tratamento de exceções centralizado em `backend/src/main/java/.../exception/` (`GlobalExceptionHandler`, `ResourceNotFoundException`, `BusinessException`) — usar esse padrão ao adicionar novos endpoints em vez de tratar erros ad-hoc nos controllers.
