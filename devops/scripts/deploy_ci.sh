#!/bin/bash
# =================================================================
# PortalCursos.NG - CI/CD Deploy Script
# Edge: nginx do host (xavierbr-vps.tech) -> upstreams portalcursos_api/web
# Triggered by: GitHub Actions on push to main
#
# DEPLOY_STRATEGY (no .env):
#   recreate  (padrão) -> recria os containers backend/frontend no lugar.
#                         Janela de ~15-20s de 502 durante o restart.
#   blue-green         -> sobe a cor ociosa (green: 8091/3011), valida o health,
#                         troca o upstream do nginx com `nginx -s reload` (sem
#                         derrubar conexão) e só então para a cor antiga.
#                         Zero downtime. Rollback = não trocar o upstream.
# =================================================================
set -euo pipefail

PROJECT_ROOT="/var/www/portalcursos"
DEVOPS_DIR="$PROJECT_ROOT/devops"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE="docker compose -f $DEVOPS_DIR/docker-compose.prod.yml --env-file $ENV_FILE"
EASYPANEL_NET="easypanel"
NGINX_UPSTREAM_CONF="/etc/nginx/conf.d/portalcursos-upstream.conf"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
ok()   { echo "[$(date '+%H:%M:%S')] OK  $*"; }
warn() { echo "[$(date '+%H:%M:%S')] AVISO $*"; }
fail() { echo "[$(date '+%H:%M:%S')] ERRO $*" >&2; exit 1; }

wait_backend() { # $1 = porta host
    local port="$1" i
    for i in $(seq 1 45); do
        curl -sf "http://127.0.0.1:${port}/api/health" 2>/dev/null | grep -q '"status":"UP"' && return 0
        sleep 2
    done
    return 1
}
wait_frontend() { # $1 = porta host
    local port="$1" i code
    for i in $(seq 1 60); do
        code=$(curl -sf -o /dev/null -w "%{http_code}" "http://127.0.0.1:${port}/portalcursos.ng" 2>/dev/null || echo "000")
        case "$code" in 200|302|308) return 0 ;; esac
        sleep 2
    done
    return 1
}
net_connect() { # $@ = nomes de container
    local c already
    for c in "$@"; do
        already=$(docker inspect "$c" --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null | tr ' ' '\n' | grep -c "^${EASYPANEL_NET}$" || true)
        [ "${already:-0}" -eq 0 ] && docker network connect "$EASYPANEL_NET" "$c" 2>/dev/null && ok "$c -> rede $EASYPANEL_NET" || true
    done
}
write_upstream() { # $1 = porta backend, $2 = porta frontend
    cat > "$NGINX_UPSTREAM_CONF" <<EOF
# Gerado pelo deploy_ci.sh em $(date '+%F %T')
upstream portalcursos_api { server 127.0.0.1:${1} max_fails=0; }
upstream portalcursos_web { server 127.0.0.1:${2} max_fails=0; }
EOF
}

# ── 1. Validacoes ────────────────────────────────────────────────
log "=== FASE 1: Validacoes ==="
[ -f "$ENV_FILE" ] || fail ".env nao encontrado em $ENV_FILE"

for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL SPRING_DATASOURCE_PASSWORD CORS_ALLOWED_ORIGINS APP_ROOT_PASSWORD APP_ADMIN_PASSWORD PORTAL_ACCESS_CODE; do
    val=$(grep "^${VAR}=" "$ENV_FILE" | cut -d= -f2- | tr -d '\r' || true)
    [ -n "$val" ] || fail "Variavel critica $VAR nao definida no .env"
done

DEPLOY_STRATEGY=$(grep "^DEPLOY_STRATEGY=" "$ENV_FILE" | cut -d= -f2- | tr -d '\r' || true)
DEPLOY_STRATEGY=${DEPLOY_STRATEGY:-recreate}
ok "Variaveis validadas — DEPLOY_STRATEGY=$DEPLOY_STRATEGY"

# ── 2. Sync do codigo ────────────────────────────────────────────
log "=== FASE 2: Sync codigo do GitHub ==="
cd "$PROJECT_ROOT"
git fetch origin main --depth=1
git reset --hard FETCH_HEAD
ok "Codigo atualizado para commit: $(git rev-parse --short HEAD)"

# ── 3. Garantir rede Traefik ─────────────────────────────────────
log "=== FASE 3: Rede $EASYPANEL_NET ==="
docker network create "$EASYPANEL_NET" 2>/dev/null && ok "Rede $EASYPANEL_NET criada" || ok "Rede $EASYPANEL_NET ja existe"

# ── 4. Build das imagens ─────────────────────────────────────────
log "=== FASE 4: Build Docker ==="
cd "$DEVOPS_DIR"
PREV_BACKEND_IMAGE_ID=$(docker inspect -f '{{.Image}}' portalcursos_backend 2>/dev/null || echo "")
PREV_BACKEND_IMAGE_TAG=$(docker inspect -f '{{.Config.Image}}' portalcursos_backend 2>/dev/null || echo "")
PREV_FRONTEND_IMAGE_ID=$(docker inspect -f '{{.Image}}' portalcursos_frontend 2>/dev/null || echo "")
PREV_FRONTEND_IMAGE_TAG=$(docker inspect -f '{{.Config.Image}}' portalcursos_frontend 2>/dev/null || echo "")
log "Buildando imagens Docker..."
$COMPOSE build backend frontend
ok "Build concluido"

BACKEND_READY=false
FRONTEND_READY=false

# ── 5. Deploy ────────────────────────────────────────────────────
if [ "$DEPLOY_STRATEGY" = "blue-green" ]; then
    log "=== FASE 5: Deploy BLUE-GREEN ==="

    # Cor ativa = a que o upstream do nginx aponta hoje (fallback: blue)
    if grep -q "127.0.0.1:8091" "$NGINX_UPSTREAM_CONF" 2>/dev/null; then
        ACTIVE=green
        IDLE_BE_SVC=backend;        IDLE_FE_SVC=frontend
        IDLE_BE_CT=portalcursos_backend;       IDLE_FE_CT=portalcursos_frontend
        IDLE_BE_PORT=8090;         IDLE_FE_PORT=3010
        ACT_BE_SVC=backend-green;   ACT_FE_SVC=frontend-green
        ACT_BE_PORT=8091;         ACT_FE_PORT=3011
    else
        ACTIVE=blue
        IDLE_BE_SVC=backend-green;  IDLE_FE_SVC=frontend-green
        IDLE_BE_CT=portalcursos_backend_green; IDLE_FE_CT=portalcursos_frontend_green
        IDLE_BE_PORT=8091;         IDLE_FE_PORT=3011
        ACT_BE_SVC=backend;         ACT_FE_SVC=frontend
        ACT_BE_PORT=8090;         ACT_FE_PORT=3010
    fi
    IDLE=$([ "$ACTIVE" = blue ] && echo green || echo blue)
    log "Cor ativa: $ACTIVE  |  subindo cor ociosa: $IDLE (be:$IDLE_BE_PORT fe:$IDLE_FE_PORT)"

    $COMPOSE up -d --force-recreate --no-deps "$IDLE_BE_SVC" "$IDLE_FE_SVC"
    net_connect "$IDLE_BE_CT" "$IDLE_FE_CT"

    if ! wait_backend "$IDLE_BE_PORT"; then
        docker logs "$IDLE_BE_CT" --tail 30 2>&1 | sed 's/^/  /'
        $COMPOSE stop "$IDLE_BE_SVC" "$IDLE_FE_SVC" || true
        $COMPOSE rm -f "$IDLE_BE_SVC" "$IDLE_FE_SVC" || true
        fail "Backend da cor $IDLE nao ficou saudavel. Producao INTACTA na cor $ACTIVE (nenhuma requisicao afetada)."
    fi
    if ! wait_frontend "$IDLE_FE_PORT"; then
        docker logs "$IDLE_FE_CT" --tail 30 2>&1 | sed 's/^/  /'
        $COMPOSE stop "$IDLE_BE_SVC" "$IDLE_FE_SVC" || true
        $COMPOSE rm -f "$IDLE_BE_SVC" "$IDLE_FE_SVC" || true
        fail "Frontend da cor $IDLE nao ficou saudavel. Producao INTACTA na cor $ACTIVE."
    fi
    ok "Cor $IDLE saudavel — trocando o upstream do nginx para ela"

    write_upstream "$IDLE_BE_PORT" "$IDLE_FE_PORT"
    if sudo nginx -t 2>/dev/null; then
        sudo nginx -s reload
        ok "nginx recarregado — trafego agora na cor $IDLE"
    else
        warn "nginx -t falhou — revertendo upstream e abortando"
        write_upstream "$ACT_BE_PORT" "$ACT_FE_PORT"
        sudo nginx -s reload 2>/dev/null || true
        $COMPOSE stop "$IDLE_BE_SVC" "$IDLE_FE_SVC" || true
        fail "Switch do nginx abortado. Producao INTACTA na cor $ACTIVE."
    fi

    sleep 5
    $COMPOSE stop "$ACT_BE_SVC" "$ACT_FE_SVC" || true
    $COMPOSE rm -f "$ACT_BE_SVC" "$ACT_FE_SVC" || true
    ok "Cor antiga $ACTIVE removida"
    BACKEND_READY=true
    FRONTEND_READY=true
    ACTIVE_BE_PORT="$IDLE_BE_PORT"

else
    # ── recreate (comportamento padrão) ──────────────────────────
    log "=== FASE 5: Deploy RECREATE ==="
    log "Subindo backend..."
    $COMPOSE up -d --force-recreate --no-deps backend
    log "Subindo frontend..."
    $COMPOSE up -d --force-recreate --no-deps frontend
    ok "Containers iniciados"
    ACTIVE_BE_PORT=8090

    log "=== FASE 6: Aguardando health checks ==="
    wait_backend 8090 && { BACKEND_READY=true; ok "Backend saudavel"; } || { warn "Backend nao respondeu:"; docker logs portalcursos_backend --tail 30 2>&1 | sed 's/^/  /'; }
    wait_frontend 3010 && { FRONTEND_READY=true; ok "Frontend saudavel"; } || { warn "Frontend nao respondeu:"; docker logs portalcursos_frontend --tail 30 2>&1 | sed 's/^/  /'; }

    if [ "$BACKEND_READY" = "false" ] || [ "$FRONTEND_READY" = "false" ]; then
        warn "Health check falhou (be:$BACKEND_READY fe:$FRONTEND_READY) — rollback para a imagem anterior..."
        ROLLBACK_OK=true
        if [ -n "$PREV_BACKEND_IMAGE_ID" ] && [ -n "$PREV_BACKEND_IMAGE_TAG" ]; then
            docker tag "$PREV_BACKEND_IMAGE_ID" "$PREV_BACKEND_IMAGE_TAG"
            $COMPOSE up -d --force-recreate --no-deps backend
            wait_backend 8090 && ok "Rollback do backend OK" || { warn "Rollback do backend falhou"; ROLLBACK_OK=false; }
        else
            warn "Sem imagem anterior de backend — rollback automatico impossivel"; ROLLBACK_OK=false
        fi
        if [ -n "$PREV_FRONTEND_IMAGE_ID" ] && [ -n "$PREV_FRONTEND_IMAGE_TAG" ]; then
            docker tag "$PREV_FRONTEND_IMAGE_ID" "$PREV_FRONTEND_IMAGE_TAG"
            $COMPOSE up -d --force-recreate --no-deps frontend
            wait_frontend 3010 && ok "Rollback do frontend OK" || { warn "Rollback do frontend falhou"; ROLLBACK_OK=false; }
        else
            warn "Sem imagem anterior de frontend — rollback automatico impossivel"; ROLLBACK_OK=false
        fi
        [ "$ROLLBACK_OK" = "true" ] \
            && fail "Deploy falhou no health check; rollback OK — producao no ar com o codigo antigo." \
            || fail "Deploy falhou no health check E o rollback tambem — intervencao manual urgente."
    fi

    log "=== FASE 7: Conectar containers a rede $EASYPANEL_NET ==="
    net_connect portalcursos_backend portalcursos_frontend
fi

# ── 8. Reload Traefik (sem restart) ─────────────────────────────
log "=== FASE 8: Reload Traefik ==="
TRAEFIK_CONTAINER=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null | head -1 || true)
if [ -n "$TRAEFIK_CONTAINER" ]; then
    docker kill --signal=SIGHUP "$TRAEFIK_CONTAINER" && ok "Traefik recarregado (SIGHUP)"
else
    warn "Container Traefik nao encontrado — reload ignorado"
fi

# ── 9. Validacao final ───────────────────────────────────────────
log "=== FASE 9: Validacao de producao ==="
sleep 5
HEALTH=$(curl -sk https://xavierbr-vps.tech/api/health 2>/dev/null || true)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    ok "API health externo: UP"
else
    warn "Health externo inconclusivo — checando interno (porta ${ACTIVE_BE_PORT})..."
    curl -sf "http://127.0.0.1:${ACTIVE_BE_PORT}/api/health" 2>/dev/null | grep -q '"status":"UP"' && ok "API health interno: OK" || warn "API health interno tambem inconclusivo"
fi
FE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" https://xavierbr-vps.tech/portalcursos.ng 2>/dev/null || echo "000")
[ "$FE_CODE" = "200" ] && ok "Frontend HTTP $FE_CODE: OK" || warn "Frontend HTTP $FE_CODE (pode ser redirect normal)"

log "=========================================="
log "Deploy concluido ($DEPLOY_STRATEGY): $(git log --oneline -1)"
log "   Backend OK:  $BACKEND_READY   Frontend OK: $FRONTEND_READY"
log "=========================================="
