# Deploy na VPS Hostinger (requer confirmação explícita)

VPS compartilhada com o CVFacil.NG, ~8 GB, ~15 containers. Este app usa ~640 MB no máximo (backend 384 MB + frontend 256 MB); o script aborta se houver < 700 MB livres.

**Isolamento:** checkout próprio em `/var/www/token-control/src` (nunca altera `/var/www/portalcursos`), compose project `token-control`, portas do host **8190 (backend) e 3110 (frontend)** — as do PortalCursos.NG são 8090/3010/8091/3011 —, não toca em `cvfacil-*`.

1. Neon: banco/branch **dedicado** (de preferência projeto separado). Histórico Flyway próprio (`token_control_schema_history`).
2. `bash setup_token_control_env.sh` — pede as connection strings (pooled e direta) com digitação oculta, testa, grava `/var/www/token-control/.env` (600). **Nunca cole a string do Neon em chat/issue/commit.** Confirme que `NEON_DIRECT_URL` **não** tem `-pooler`.
3. Containers: `bash devops/scripts/deploy_token_control.sh` (REF padrão = branch de desenvolvimento; após o merge, `REF=main`).
4. **Roteamento (edge):** em 2026-10-02 as portas 80/443 da VPS estavam com o `docker-proxy` (Traefik do EasyPanel) e o `nginx.service` do host estava `failed` desde 2026-10-01 06:44 (bind em uso). Por isso o frontend traz rótulos Traefik (`PathPrefix(/tokencontrol)`, rede `easypanel`), como o PortalCursos.NG — não há passo de nginx. `apply_token_control_nginx.sh` (+ `nginx-snippet.conf`) só serve se o nginx do host voltar a ser o edge; **não usar** enquanto o nginx estiver parado (o `reload` falha: pidfile vazio).
5. URL: `https://xavierbr-vps.tech/tokencontrol` (login Basic: `DASHBOARD_USER`/`DASHBOARD_PASSWORD`).
6. Máquina local (onde roda o Claude Code): `TOKEN_CONTROL_URL=https://xavierbr-vps.tech/tokencontrol TOKEN_CONTROL_API_KEY=... node token-control/collector/collect.mjs --watch 300`.

Rollback: `docker compose -p token-control down` (dados ficam no Neon); no nginx, o comando de rollback impresso por `apply_token_control_nginx.sh`.
