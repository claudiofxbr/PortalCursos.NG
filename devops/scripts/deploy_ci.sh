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
EASYPANEL_NET="easypanel"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
ok()   { echo "[$(date '+%H:%M:%S')] OK  $*"; }
warn() { echo "[$(date '+%H:%M:%S')] AVISO $*"; }
fail() { echo "[$(date '+%H:%M:%S')] ERRO $*" >&2; exit 1; }

# ── 1. Validacoes ────────────────────────────────────────────────
log "=== FASE 1: Validacoes ==="
[ -f "$ENV_FILE" ] || fail ".env nao encontrado em $ENV_FILE"

for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL SPRING_DATASOURCE_PASSWORD CORS_ALLOWED_ORIGINS APP_ROOT_PASSWORD APP_ADMIN_PASSWORD PORTAL_ACCESS_CODE; do
    val=$(grep "^${VAR}=" "$ENV_FILE" | cut -d= -f2- | tr -d '\r' || true)
    [ -n "$val" ] || fail "Variavel critica $VAR nao definida no .env"
done
ok "Variaveis de ambiente validadas"

# ── 2. Sync do codigo ────────────────────────────────────────────
log "=== FASE 2: Sync codigo do GitHub ==="
cd "$PROJECT_ROOT"
git fetch origin main --depth=1
git reset --hard FETCH_HEAD
ok "Codigo atualizado para commit: $(git rev-parse --short HEAD)"

# ── 3. Garantir rede Traefik ─────────────────────────────────────
log "=== FASE 3: Rede Traefik ==="
docker network create "$EASYPANEL_NET" 2>/dev/null && ok "Rede $EASYPANEL_NET criada" || ok "Rede $EASYPANEL_NET ja existe"

# ── 4. Build das imagens ─────────────────────────────────────────
log "=== FASE 4: Build Docker ==="
cd "$DEVOPS_DIR"

# Guarda a imagem/tag anteriores de cada container para permitir rollback
# se o health check da FASE 6 falhar (evita deixar producao fora do ar).
PREV_BACKEND_IMAGE_ID=$(docker inspect -f '{{.Image}}' portalcursos_backend 2>/dev/null || echo "")
PREV_BACKEND_IMAGE_TAG=$(docker inspect -f '{{.Config.Image}}' portalcursos_backend 2>/dev/null || echo "")
PREV_FRONTEND_IMAGE_ID=$(docker inspect -f '{{.Image}}' portalcursos_frontend 2>/dev/null || echo "")
PREV_FRONTEND_IMAGE_TAG=$(docker inspect -f '{{.Config.Image}}' portalcursos_frontend 2>/dev/null || echo "")

log "Buildando imagens Docker..."
$COMPOSE build backend frontend
ok "Build concluido"

# ── 5. Deploy na ordem correta: postgres → backend → frontend ─────
log "=== FASE 5: Deploy na ordem correta ==="

log "Subindo PostgreSQL..."
$COMPOSE up -d postgres

log "Aguardando PostgreSQL ficar saudavel (max 60s)..."
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
    warn "PostgreSQL nao respondeu em 60s — continuando mesmo assim (Neon externo pode estar em uso)"
fi

log "Subindo backend..."
$COMPOSE up -d --force-recreate --no-deps backend

log "Subindo frontend..."
$COMPOSE up -d --force-recreate --no-deps frontend

ok "Containers iniciados"

# ── 6. Aguardar health dos containers ────────────────────────────
log "=== FASE 6: Aguardando health checks ==="
BACKEND_READY=false
for i in $(seq 1 45); do
    if curl -sf http://127.0.0.1:8090/api/health > /dev/null 2>&1; then
        ok "Backend saudavel (${i}x2s)"
        BACKEND_READY=true
        break
    fi
    if [ "$i" -eq 45 ]; then
        warn "Backend nao respondeu em 90s — logs:"
        docker logs portalcursos_backend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

FRONTEND_READY=false
for i in $(seq 1 60); do
    # Testa o path real da aplicacao — redirecionar (308) ou 200 sao validos
    CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010/portalcursos.ng 2>/dev/null || echo "000")
    if [ "$CODE" = "200" ] || [ "$CODE" = "308" ] || [ "$CODE" = "302" ]; then
        ok "Frontend respondendo HTTP $CODE (${i}x2s)"
        FRONTEND_READY=true
        break
    fi
    if [ "$i" -eq 60 ]; then
        warn "Frontend nao respondeu em 120s — logs:"
        docker logs portalcursos_frontend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

if [ "$BACKEND_READY" = "false" ] || [ "$FRONTEND_READY" = "false" ]; then
    warn "Health check falhou — Backend OK: $BACKEND_READY, Frontend OK: $FRONTEND_READY. Tentando rollback para a imagem anterior..."
    # Reverte backend E frontend juntos, mesmo que so um dos dois tenha falhado no
    # health check — evita deixar um par de versoes incompativel em producao
    # (ex.: frontend novo fazendo requisicoes a um backend com contrato antigo).

    ROLLBACK_OK=true
    if [ -n "$PREV_BACKEND_IMAGE_ID" ] && [ -n "$PREV_BACKEND_IMAGE_TAG" ]; then
        docker tag "$PREV_BACKEND_IMAGE_ID" "$PREV_BACKEND_IMAGE_TAG"
        $COMPOSE up -d --force-recreate --no-deps backend
        RB_BACKEND_READY=false
        for i in $(seq 1 45); do
            if curl -sf http://127.0.0.1:8090/api/health > /dev/null 2>&1; then
                RB_BACKEND_READY=true
                break
            fi
            sleep 2
        done
        [ "$RB_BACKEND_READY" = "true" ] && ok "Rollback do backend OK" || { warn "Rollback do backend tambem falhou"; ROLLBACK_OK=false; }
    else
        warn "Sem imagem anterior de backend conhecida — rollback automatico nao e possivel"
        ROLLBACK_OK=false
    fi

    if [ -n "$PREV_FRONTEND_IMAGE_ID" ] && [ -n "$PREV_FRONTEND_IMAGE_TAG" ]; then
        docker tag "$PREV_FRONTEND_IMAGE_ID" "$PREV_FRONTEND_IMAGE_TAG"
        $COMPOSE up -d --force-recreate --no-deps frontend
        RB_FRONTEND_READY=false
        for i in $(seq 1 60); do
            CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010/portalcursos.ng 2>/dev/null || echo "000")
            if [ "$CODE" = "200" ] || [ "$CODE" = "308" ] || [ "$CODE" = "302" ]; then
                RB_FRONTEND_READY=true
                break
            fi
            sleep 2
        done
        [ "$RB_FRONTEND_READY" = "true" ] && ok "Rollback do frontend OK" || { warn "Rollback do frontend tambem falhou"; ROLLBACK_OK=false; }
    else
        warn "Sem imagem anterior de frontend conhecida — rollback automatico nao e possivel"
        ROLLBACK_OK=false
    fi

    if [ "$ROLLBACK_OK" = "true" ]; then
        fail "Deploy da nova versao falhou no health check, mas o rollback para a versao anterior teve sucesso — producao permanece no ar com o codigo antigo. Corrija o problema e tente novamente."
    else
        fail "Deploy da nova versao falhou no health check E o rollback automatico tambem falhou — producao pode estar fora do ar. Intervencao manual urgente necessaria."
    fi
fi

# ── 7. Reconectar a rede Traefik ─────────────────────────────────
log "=== FASE 7: Conectar containers a rede Traefik ==="
for container in portalcursos_backend portalcursos_frontend; do
    already=$(docker inspect "$container" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null \
        | tr ' ' '\n' | grep -c "^${EASYPANEL_NET}$" || true)
    if [ "${already:-0}" -eq 0 ]; then
        docker network connect "$EASYPANEL_NET" "$container" && ok "$container -> rede $EASYPANEL_NET"
    else
        ok "$container ja esta na rede $EASYPANEL_NET"
    fi
done

# ── 8. Reload Traefik (sem restart) ─────────────────────────────
log "=== FASE 8: Reload Traefik ==="
TRAEFIK_CONTAINER=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null | head -1 || true)
if [ -n "$TRAEFIK_CONTAINER" ]; then
    docker kill --signal=SIGHUP "$TRAEFIK_CONTAINER"
    ok "Traefik recarregado (SIGHUP)"
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
    warn "Health check externo inconclusivo — verificando interno..."
    if curl -sf http://127.0.0.1:8090/api/health 2>/dev/null | grep -q '"status":"UP"'; then
        ok "API health interno: OK"
    fi
fi

FE_CODE=$(curl -sk -o /dev/null -w "%{http_code}" https://xavierbr-vps.tech/portalcursos.ng 2>/dev/null || echo "000")
[ "$FE_CODE" = "200" ] && ok "Frontend HTTP $FE_CODE: OK" || warn "Frontend HTTP $FE_CODE (pode ser redirect normal)"

log "=========================================="
log "Deploy concluido: $(git log --oneline -1)"
log "   Backend:     https://xavierbr-vps.tech/api/health"
log "   Frontend:    https://xavierbr-vps.tech/portalcursos.ng"
log "   Backend OK:  $BACKEND_READY"
log "   Frontend OK: $FRONTEND_READY"
log "=========================================="
