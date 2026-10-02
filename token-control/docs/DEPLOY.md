# Deploy na VPS Hostinger (requer confirmação explícita)

VPS compartilhada com o CVFacil.NG, ~8 GB, ~15 containers. Este app usa ~640 MB no máximo (backend 384 MB + frontend 256 MB); o script aborta se houver < 700 MB livres. Não toca em `portalcursos` nem `cvfacil-*`.

1. Neon: crie um banco/branch **dedicado** e copie a URL JDBC (`?sslmode=require`).
2. Na VPS: `bash devops/scripts/setup_token_control_env.sh` — pede as connection strings do Neon (pooled e direta) com a digitação oculta, testa a conexão, gera a chave do app e grava `/var/www/token-control/.env` com permissão 600. **Nunca cole a string do Neon em chat/issue/commit.**
3. `bash devops/scripts/deploy_token_control.sh` (faz `git merge --ff-only origin/main`, `docker compose up -d --build`, valida o health).
4. nginx: incluir `token-control/nginx-snippet.conf` no server de `xavierbr-vps.tech`, depois `nginx -t && nginx -s reload`. URL: `https://xavierbr-vps.tech/tokencontrol/`.
5. Máquina local (onde roda o Claude Code): `TOKEN_CONTROL_URL=https://xavierbr-vps.tech/tokencontrol TOKEN_CONTROL_API_KEY=... node token-control/collector/collect.mjs --watch 300`.

Rollback: `docker compose -f token-control/docker-compose.yml down` (dados ficam no Neon); remover o `location` do nginx.
