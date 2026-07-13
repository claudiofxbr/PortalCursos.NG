#!/bin/bash
# deploy_vps.sh — Executado NA VPS pelo deploy_github_to_vps.ps1
# PortalCursos.NG | xavierbr-vps.tech
set -euo pipefail

PROJECT_ROOT="/var/www/portalcursos"
GIT_REPO="https://github.com/claudiofxbr/PortalCursos.NG"
GIT_BRANCH="main"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE_FILE="$PROJECT_ROOT/devops/docker-compose.prod.yml"
COMPOSE="docker compose -f $COMPOSE_FILE --env-file $ENV_FILE"
APP_URL="https://xavierbr-vps.tech/portalcursos.ng"
API_URL="https://xavierbr-vps.tech/api/health"

ok()   { echo "[OK]    $*"; }
info() { echo "[INFO]  $*"; }
warn() { echo "[AVISO] $*"; }
fail() { echo "[ERRO]  $*" >&2; exit 1; }

echo ""
echo "============================================"
echo "  PortalCursos.NG — Deploy Manual na VPS"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Servidor: $(hostname)"
echo "============================================"
echo ""

# ── 1. Pre-requisitos na VPS ─────────────────────────────────────
info "Verificando pre-requisitos..."
command -v docker        > /dev/null 2>&1 || fail "Docker nao encontrado. Execute: setup_server.sh"
docker compose version   > /dev/null 2>&1 || fail "Docker Compose (plugin v2) nao encontrado."
command -v git           > /dev/null 2>&1 || fail "Git nao encontrado."
ok "Docker, Compose e Git disponiveis"

# ── 2. Clone inicial (so na primeira vez) ────────────────────────
if [ ! -d "$PROJECT_ROOT/.git" ]; then
    info "Primeira execucao — clonando repositorio..."
    mkdir -p "$PROJECT_ROOT"
    git clone "$GIT_REPO" "$PROJECT_ROOT"
    ok "Repositorio clonado em $PROJECT_ROOT"
fi

cd "$PROJECT_ROOT"

# ── 3. Verificar .env ────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
    echo ""
    fail ".env nao encontrado em $ENV_FILE
Crie o arquivo com:
  APP_JWT_SECRET=<minimo-64-chars>
  SPRING_DATASOURCE_URL=jdbc:postgresql://<host>/neondb?sslmode=require
  SPRING_DATASOURCE_USERNAME=<usuario>
  SPRING_DATASOURCE_PASSWORD=<senha>
  CORS_ALLOWED_ORIGINS=https://xavierbr-vps.tech
  DOMAIN_NAME=xavierbr-vps.tech
  NEXT_PUBLIC_API_URL=https://xavierbr-vps.tech
  NEXT_PUBLIC_BASE_PATH=/portalcursos.ng
  APP_SECURE_COOKIES=true
  PORTAL_ACCESS_CODE=<codigo>
  APP_ROOT_PASSWORD=<senha-root>
  APP_ADMIN_PASSWORD=<senha-admin>"
fi

for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL CORS_ALLOWED_ORIGINS DOMAIN_NAME APP_ROOT_PASSWORD APP_ADMIN_PASSWORD PORTAL_ACCESS_CODE; do
    VAL=$(grep "^${VAR}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '\r' || true)
    [ -n "$VAL" ] || fail "Variavel $VAR ausente ou vazia no .env"
done
ok ".env validado"

# ── 4. Rede Traefik ───────────────────────────────────────────────
docker network create easypanel 2>/dev/null && ok "Rede easypanel criada" || ok "Rede easypanel ja existe"

# ── 5. Sincronizar codigo ─────────────────────────────────────────
info "Sincronizando codigo do GitHub..."
git fetch origin "$GIT_BRANCH" --depth=1
git reset --hard FETCH_HEAD
ok "Codigo: $(git log --oneline -1)"

# ── 6. Build das imagens ──────────────────────────────────────────
info "Buildando imagens Docker (backend e frontend)..."
$COMPOSE build backend frontend
ok "Build concluido"

# ── 7. Deploy na ordem correta: postgres → backend → frontend ─────
info "Subindo PostgreSQL..."
$COMPOSE up -d postgres

info "Aguardando PostgreSQL ficar saudavel (max 60s)..."
PG_READY=false
for i in $(seq 1 30); do
    if $COMPOSE exec -T postgres pg_isready -U "${SPRING_DATASOURCE_USERNAME:-portal_admin}" -d portalcursos_db > /dev/null 2>&1; then
        ok "PostgreSQL saudavel"
        PG_READY=true
        break
    fi
    sleep 2
done
if [ "$PG_READY" = "false" ]; then
    warn "PostgreSQL nao respondeu em 60s — logs:"
    docker logs portalcursos_postgres --tail 20 2>&1 | sed 's/^/  /'
fi

info "Subindo backend..."
$COMPOSE up -d --force-recreate --no-deps backend

info "Aguardando backend responder (max 90s)..."
BE_UP=false
for i in $(seq 1 45); do
    if curl -sf http://127.0.0.1:8090/api/health > /dev/null 2>&1; then
        ok "Backend respondendo"
        BE_UP=true
        break
    fi
    if [ "$i" -eq 45 ]; then
        warn "Backend nao respondeu em 90s — logs:"
        docker logs portalcursos_backend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

info "Subindo frontend..."
$COMPOSE up -d --force-recreate --no-deps frontend

info "Aguardando frontend responder em /portalcursos.ng (max 120s)..."
FE_UP=false
for i in $(seq 1 60); do
    CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010/portalcursos.ng 2>/dev/null || echo "000")
    if [ "$CODE" = "200" ] || [ "$CODE" = "308" ] || [ "$CODE" = "302" ]; then
        ok "Frontend respondendo (HTTP $CODE)"
        FE_UP=true
        break
    fi
    if [ "$i" -eq 60 ]; then
        warn "Frontend nao respondeu em 120s — logs:"
        docker logs portalcursos_frontend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

# ── 8. Reconectar rede Traefik ────────────────────────────────────
for CTR in portalcursos_backend portalcursos_frontend; do
    IN_NET=$(docker inspect "$CTR" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null \
        | tr ' ' '\n' | grep -c "^easypanel$" || true)
    if [ "${IN_NET:-0}" -eq 0 ]; then
        docker network connect easypanel "$CTR" && ok "$CTR conectado ao Traefik"
    else
        ok "$CTR ja esta na rede Traefik"
    fi
done

# ── 9. Reload Traefik ─────────────────────────────────────────────
TRAEFIK_ID=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null | head -1 || true)
if [ -n "$TRAEFIK_ID" ]; then
    docker kill --signal=SIGHUP "$TRAEFIK_ID" && ok "Traefik recarregado"
else
    warn "Container Traefik nao encontrado — reload ignorado"
fi

# ── 10. Resultado ─────────────────────────────────────────────────
sleep 5
FE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$APP_URL" 2>/dev/null || echo "000")
BE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" "$API_URL" 2>/dev/null || echo "000")

echo ""
echo "============================================"
echo "  Resultado do deploy:"
echo "  Frontend $APP_URL : HTTP $FE_CODE"
echo "  Backend  $API_URL : HTTP $BE_CODE"
echo ""
if [ "$BE_UP" = "true" ] && [ "$FE_UP" = "true" ]; then
    echo "  STATUS: SUCESSO"
    echo "  Acesse: $APP_URL"
else
    echo "  STATUS: PARCIAL — verifique logs acima"
    echo "  Backend  OK: $BE_UP"
    echo "  Frontend OK: $FE_UP"
fi
echo "============================================"
