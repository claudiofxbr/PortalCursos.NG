#!/bin/bash
# Deploy do Controle de Tokens Claude Code na VPS Hostinger (stack própria, isolada).
# ATENÇÃO: ato real de produção — só rode com confirmação explícita do dono do projeto.
# Não toca em portalcursos nem em cvfacil-*. Não altera o nginx (aplicar docs/DEPLOY.md à mão, com nginx -t).
set -euo pipefail

APP_DIR="${APP_DIR:-/var/www/token-control}"
REPO_DIR="${REPO_DIR:-/var/www/portalcursos}"          # clone do repositório já existente na VPS
ENV_FILE="$APP_DIR/.env"
COMPOSE="docker compose -f $REPO_DIR/token-control/docker-compose.yml --env-file $ENV_FILE"

[ -f "$ENV_FILE" ] || { echo "ERRO: $ENV_FILE não existe (copie token-control/.env.example e preencha)"; exit 1; }
free_mb=$(free -m | awk '/^Mem:/{print $7}')
[ "$free_mb" -gt 700 ] || { echo "ERRO: só ${free_mb}MB de RAM disponível (VPS compartilhada) — abortando"; exit 1; }

cd "$REPO_DIR" && git fetch origin main && git merge --ff-only origin/main
$COMPOSE up -d --build

for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8090/actuator/health >/dev/null 2>&1; then
    echo "OK backend saudável"; $COMPOSE ps; exit 0
  fi
  sleep 5
done
echo "ERRO: backend não ficou saudável em 150s"; $COMPOSE logs --tail=50 backend; exit 1
