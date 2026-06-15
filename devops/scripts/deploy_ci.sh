#!/bin/bash
# =================================================================
# PortalCursos.NG - CI/CD Deploy Script
# Stack: Docker Compose + Traefik (EasyPanel)
# Triggered by: GitHub Actions on push to main
# =================================================================
set -eo pipefail

PROJECT_ROOT="/var/www/portalcursos"
DEVOPS_DIR="$PROJECT_ROOT/devops"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE="docker compose -f $DEVOPS_DIR/docker-compose.prod.yml --env-file $ENV_FILE"
EASYPANEL_NET="easypanel"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
ok()   { echo "[$(date '+%H:%M:%S')] ✅ $*"; }
warn() { echo "[$(date '+%H:%M:%S')] ⚠️  $*"; }
fail() { echo "[$(date '+%H:%M:%S')] ❌ $*" >&2; exit 1; }

# ── 1. Validações ────────────────────────────────────────────────
log "=== FASE 1: Validações ==="
[ -f "$ENV_FILE" ] || fail ".env não encontrado em $ENV_FILE"

for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL CORS_ALLOWED_ORIGINS APP_ROOT_PASSWORD APP_ADMIN_PASSWORD; do
    val=$(grep "^${VAR}=" "$ENV_FILE" | cut -d= -f2- | tr -d '\r' || true)
    [ -n "$val" ] || fail "Variavel critica $VAR nao definida no .env"
done
ok "Variáveis de ambiente validadas"

# ── 2. Sync do código ────────────────────────────────────────────
log "=== FASE 2: Sync código do GitHub ==="
cd "$PROJECT_ROOT"
git fetch origin main --depth=1
git reset --hard FETCH_HEAD
ok "Código atualizado para commit: $(git rev-parse --short HEAD)"

# ── 3. Garantir rede Traefik ─────────────────────────────────────
log "=== FASE 3: Rede Traefik ==="
docker network create "$EASYPANEL_NET" 2>/dev/null && ok "Rede $EASYPANEL_NET criada" || ok "Rede $EASYPANEL_NET já existe"

# ── 4. Build e deploy ────────────────────────────────────────────
log "=== FASE 4: Build e Deploy Docker ==="
cd "$DEVOPS_DIR"

log "Buildando imagens Docker..."
$COMPOSE build backend frontend

log "Subindo containers..."
$COMPOSE up -d --force-recreate --no-deps backend frontend

# Garantir que postgres continua rodando
$COMPOSE up -d postgres
ok "Containers iniciados"

# ── 5. Aguardar health dos containers ────────────────────────────
log "=== FASE 5: Aguardando health checks ==="
BACKEND_READY=false
for i in $(seq 1 45); do
    if curl -sf http://127.0.0.1:8090/api/health > /dev/null 2>&1; then
        ok "Backend saudável (${i}x2s)"
        BACKEND_READY=true
        break
    fi
    if [ "$i" -eq 45 ]; then
        warn "Backend não respondeu em 90s — logs:"
        docker logs portalcursos_backend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

FRONTEND_READY=false
for i in $(seq 1 60); do
    CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010 2>/dev/null || echo "000")
    if [ "$CODE" != "000" ]; then
        ok "Frontend respondendo HTTP $CODE (${i}x2s)"
        FRONTEND_READY=true
        break
    fi
    if [ "$i" -eq 60 ]; then
        warn "Frontend não respondeu em 120s — logs:"
        docker logs portalcursos_frontend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

# ── 6. Reconectar à rede Traefik ─────────────────────────────────
log "=== FASE 6: Conectar containers à rede Traefik ==="
for container in portalcursos_backend portalcursos_frontend; do
    # || true evita exit 1 quando grep não encontra o resultado (set -e)
    already=$(docker inspect "$container" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null \
        | tr ' ' '\n' | grep -c "^${EASYPANEL_NET}$" || true)
    if [ "${already:-0}" -eq 0 ]; then
        docker network connect "$EASYPANEL_NET" "$container" && ok "$container → rede $EASYPANEL_NET"
    else
        ok "$container já está na rede $EASYPANEL_NET"
    fi
done

# ── 7. Reload Traefik (sem restart) ─────────────────────────────
log "=== FASE 7: Reload Traefik ==="
TRAEFIK_CONTAINER=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null | head -1 || true)
if [ -n "$TRAEFIK_CONTAINER" ]; then
    docker kill --signal=SIGHUP "$TRAEFIK_CONTAINER"
    ok "Traefik recarregado (SIGHUP)"
else
    warn "Container Traefik não encontrado — reload ignorado"
fi

# ── 8. Validação final ───────────────────────────────────────────
log "=== FASE 8: Validação de produção ==="
sleep 5

HEALTH=$(curl -sk https://xavierbr-vps.tech/api/health 2>/dev/null || true)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    ok "API health externo: UP"
else
    warn "Health check externo inconclusivo — verificando interno..."
    if curl -sf http://127.0.0.1:8090/api/health 2>/dev/null | grep -q '"status":"UP"'; then
        ok "API health interno: OK"
    fi
fi

FE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" https://xavierbr-vps.tech/portalcursos.ng 2>/dev/null || echo "000")
[ "$FE_CODE" = "200" ] && ok "Frontend HTTP $FE_CODE: OK" || warn "Frontend HTTP $FE_CODE"

log "=========================================="
log "🎉 Deploy concluído: $(git log --oneline -1)"
log "   Backend:  https://xavierbr-vps.tech/api/health"
log "   Frontend: https://xavierbr-vps.tech/portalcursos.ng"
log "   Backend OK:  $BACKEND_READY"
log "   Frontend OK: $FRONTEND_READY"
log "=========================================="
