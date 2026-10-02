#!/bin/bash
# Deploy do Controle de Tokens Claude Code na VPS Hostinger (stack própria, isolada).
# ATENÇÃO: ato real de produção — só rode com confirmação explícita do dono do projeto.
#
# Isolamento: usa um checkout PRÓPRIO em $APP_DIR/src (nunca altera /var/www/portalcursos),
# portas do host 8190/3110 (as do PortalCursos.NG são 8090/3010/8091/3011), compose project "token-control".
# Não toca em cvfacil-* nem no nginx (isso é apply_token_control_nginx.sh, separado).
set -euo pipefail

APP_DIR="${APP_DIR:-/var/www/token-control}"
SRC_DIR="$APP_DIR/src"
ENV_FILE="$APP_DIR/.env"
REF="${REF:-claude/bold-mayer-tdvqlf}"            # depois do merge na main: REF=main
PORTAL_CLONE="${PORTAL_CLONE:-/var/www/portalcursos}"  # só LEITURA: de onde copiamos a URL do remote
REPO_URL="${REPO_URL:-$(git -C "$PORTAL_CLONE" remote get-url origin)}"
COMPOSE="docker compose -f $SRC_DIR/token-control/docker-compose.yml --env-file $ENV_FILE"

[ -f "$ENV_FILE" ] || { echo "ERRO: $ENV_FILE não existe (rode setup_token_control_env.sh)"; exit 1; }
free_mb=$(free -m | awk '/^Mem:/{print $7}')
[ "$free_mb" -gt 700 ] || { echo "ERRO: só ${free_mb}MB de RAM disponível (VPS compartilhada) — abortando"; exit 1; }

# Portas: se não for este stack já rodando, 8190/3110 precisam estar livres
if ! docker compose -p token-control ps -q 2>/dev/null | grep -q .; then
  for port in 8190 3110; do
    if ss -ltn "sport = :$port" | grep -q LISTEN; then echo "ERRO: porta $port já em uso por outro serviço — abortando"; exit 1; fi
  done
fi

if [ -d "$SRC_DIR/.git" ]; then
  git -C "$SRC_DIR" fetch --depth 1 origin "$REF"
  git -C "$SRC_DIR" checkout -q -B "$REF" FETCH_HEAD
else
  mkdir -p "$APP_DIR"
  git clone --depth 1 --branch "$REF" --single-branch "$REPO_URL" "$SRC_DIR"
fi
echo "Checkout: $(git -C "$SRC_DIR" rev-parse --short HEAD) ($REF)"

$COMPOSE up -d --build

for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8190/actuator/health >/dev/null 2>&1; then
    code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3110/tokencontrol || true)
    echo "OK backend saudável; frontend HTTP $code (401 = login exigido, correto)"
    $COMPOSE ps; exit 0
  fi
  sleep 5
done
echo "ERRO: backend não ficou saudável em 150s"; $COMPOSE logs --tail=50 backend; exit 1
