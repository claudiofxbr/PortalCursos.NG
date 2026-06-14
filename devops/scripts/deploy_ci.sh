#!/bin/bash
# =================================================================
# PortalCursos.NG - CI/CD Deploy Script
# Stack: Docker Compose + Traefik (EasyPanel)
# Triggered by: GitHub Actions on push to main
# =================================================================
set -euo pipefail

PROJECT_ROOT="/var/www/portalcursos"
DEVOPS_DIR="$PROJECT_ROOT/devops"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE="docker compose -f $DEVOPS_DIR/docker-compose.prod.yml --env-file $ENV_FILE"
TRAEFIK_CONTAINER=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null || true)
EASYPANEL_NET="easypanel"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
ok()   { echo "[$(date '+%H:%M:%S')] ✅ $*"; }
warn() { echo "[$(date '+%H:%M:%S')] ⚠️  $*"; }
fail() { echo "[$(date '+%H:%M:%S')] ❌ $*" >&2; exit 1; }

# ── 1. Validações ────────────────────────────────────────────────
log "=== FASE 1: Validações ==="
[ -f "$ENV_FILE" ] || fail ".env não encontrado em $ENV_FILE"

for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL CORS_ALLOWED_ORIGINS; do
    val=$(grep "^${VAR}=" "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
    [ -n "$val" ] || fail "Variavel critica $VAR nao definida no .env"
done
ok "Variáveis de ambiente validadas"

# ── 2. Sync do código ────────────────────────────────────────────
log "=== FASE 2: Sync código do GitHub ==="
cd "$PROJECT_ROOT"
git fetch origin main --depth=1
git reset --hard FETCH_HEAD
ok "Código atualizado para commit: $(git rev-parse --short HEAD)"

# ── 3. Build e deploy ────────────────────────────────────────────
log "=== FASE 3: Build e Deploy Docker ==="
cd "$DEVOPS_DIR"

# Rebuild apenas das imagens modificadas (cache inteligente)
log "Buildando imagens Docker..."
$COMPOSE build backend frontend

# Subir com recriação forçada
log "Subindo containers..."
$COMPOSE up -d --force-recreate --no-deps backend frontend

# Garantir que postgres continua rodando
$COMPOSE up -d postgres
ok "Containers iniciados"

# ── 4. Aguardar health dos containers ────────────────────────────
log "=== FASE 4: Aguardando health checks ==="
for i in $(seq 1 30); do
    be_status=$(docker inspect --format='{{.State.Health.Status}}' portalcursos_backend 2>/dev/null || echo "starting")
    if [ "$be_status" = "healthy" ] || [ "$be_status" = "running" ]; then
        # Verificar health endpoint diretamente
        if docker exec portalcursos_backend wget -qO- http://localhost:8080/api/health 2>/dev/null | grep -q '"status":"UP"'; then
            ok "Backend saudável"
            break
        fi
    fi
    if [ "$i" -eq 30 ]; then
        warn "Backend não respondeu em 60s — verificando logs..."
        docker logs portalcursos_backend --tail 20
    fi
    sleep 2
done

# ── 5. Reconectar à rede Traefik ─────────────────────────────────
log "=== FASE 5: Conectar containers à rede Traefik ==="
for container in portalcursos_backend portalcursos_frontend; do
    already=$(docker inspect "$container" --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null | tr ' ' '\n' | grep -c "^${EASYPANEL_NET}$" || true)
    if [ "$already" -eq 0 ]; then
        docker network connect "$EASYPANEL_NET" "$container" && ok "$container → rede $EASYPANEL_NET"
    else
        ok "$container já está na rede $EASYPANEL_NET"
    fi
done

# ── 6. Reload Traefik (sem restart) ─────────────────────────────
log "=== FASE 6: Reload Traefik ==="
if [ -n "$TRAEFIK_CONTAINER" ]; then
    docker kill --signal=SIGHUP "$TRAEFIK_CONTAINER"
    ok "Traefik recarregado (SIGHUP)"
else
    warn "Container Traefik não encontrado — reload ignorado"
fi

# ── 7. Validação final ───────────────────────────────────────────
log "=== FASE 7: Validação de produção ==="
sleep 3

HEALTH=$(curl -sk https://xavierbr-vps.tech/api/health 2>/dev/null || true)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    ok "API health: UP"
else
    warn "Health check externo falhou — pode ser DNS/rede do runner. Verificando internamente..."
    docker exec portalcursos_backend wget -qO- http://localhost:8080/api/health | grep -q '"status":"UP"' && ok "API health interno: OK"
fi

FE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" https://xavierbr-vps.tech/portalcursos.ng 2>/dev/null || echo "000")
if [ "$FE_CODE" = "200" ]; then
    ok "Frontend HTTP $FE_CODE: OK"
else
    warn "Frontend retornou HTTP $FE_CODE (pode ser DNS do runner)"
fi

log "=========================================="
log "🎉 Deploy concluído: $(git log --oneline -1)"
log "   Backend:  https://xavierbr-vps.tech/api/health"
log "   Frontend: https://xavierbr-vps.tech/portalcursos.ng"
log "=========================================="
