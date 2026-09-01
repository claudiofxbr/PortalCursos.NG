#!/bin/bash
# =================================================================
# fix_502.sh - Corrige 502 Bad Gateway (arquitetura Traefik/EasyPanel)
# PortalCursos.NG | xavierbr-vps.tech
#
# Executar NA VPS como root:
#   sudo bash /tmp/fix_502.sh
#
# Arquitetura:
#   Traefik (EasyPanel) ocupa portas 80/443.
#   Os containers se registram via labels Docker.
#   Nginx NAO e usado — Traefik e o unico proxy de borda.
# =================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log()     { echo -e "${BLUE}[$(date '+%H:%M:%S')] INFO${NC}  $*"; }
ok()      { echo -e "${GREEN}[$(date '+%H:%M:%S')] OK${NC}    $*"; }
warn()    { echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARN${NC}  $*"; }
fail()    { echo -e "${RED}[$(date '+%H:%M:%S')] ERRO${NC}  $*" >&2; exit 1; }
section() { echo -e "\n${CYAN}=== $* ===${NC}"; }

if [ "$EUID" -ne 0 ]; then
    fail "Execute como root: sudo bash fix_502.sh"
fi

PROJECT_ROOT="/var/www/portalcursos"
DEVOPS_DIR="$PROJECT_ROOT/devops"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE_FILE="$DEVOPS_DIR/docker-compose.prod.yml"
COMPOSE="docker compose -f $COMPOSE_FILE --env-file $ENV_FILE"
DOMAIN="xavierbr-vps.tech"

echo ""
echo "=================================================================="
echo "  FIX 502 Bad Gateway - PortalCursos.NG"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Arquitetura: Traefik (EasyPanel) + Docker Compose"
echo "=================================================================="
echo ""

# ================================================================
section "FASE 1: Parar Nginx (conflita com Traefik na porta 80/443)"
# ================================================================

if command -v nginx &>/dev/null; then
    if systemctl is-active --quiet nginx 2>/dev/null; then
        systemctl stop nginx
        ok "Nginx parado."
    else
        log "Nginx instalado mas ja inativo."
    fi
    systemctl disable nginx 2>/dev/null || true
    ok "Nginx desabilitado (Traefik e o proxy de borda)."
else
    ok "Nginx nao instalado — nenhuma acao necessaria."
fi

# ================================================================
section "FASE 2: Verificar Traefik ativo na VPS"
# ================================================================

TRAEFIK_ID=$(docker ps --filter name=easypanel-traefik --filter status=running -q 2>/dev/null | head -1 || true)
if [ -z "$TRAEFIK_ID" ]; then
    fail "Traefik do EasyPanel nao esta rodando. Acesse o painel EasyPanel e verifique o status."
fi
ok "Traefik ativo: $TRAEFIK_ID"

# Confirma que Traefik escuta nas portas 80 e 443
PORT_CHECK=$(docker inspect "$TRAEFIK_ID" \
    --format '{{range $p, $conf := .NetworkSettings.Ports}}{{$p}} {{end}}' 2>/dev/null || echo "")
log "Portas Traefik: $PORT_CHECK"

# ================================================================
section "FASE 3: Verificar rede 'easypanel' existe"
# ================================================================

if ! docker network ls 2>/dev/null | grep -q "easypanel"; then
    fail "Rede Docker 'easypanel' nao encontrada. EasyPanel cria esta rede automaticamente."
fi
ok "Rede 'easypanel' existe."

# ================================================================
section "FASE 4: Verificar arquivos do projeto na VPS"
# ================================================================

if [ ! -d "$PROJECT_ROOT" ]; then
    fail "Diretorio do projeto nao encontrado: $PROJECT_ROOT"
fi
ok "Diretorio do projeto: $PROJECT_ROOT"

if [ ! -f "$COMPOSE_FILE" ]; then
    fail "docker-compose.prod.yml nao encontrado: $COMPOSE_FILE"
fi
ok "docker-compose.prod.yml encontrado."

# ================================================================
section "FASE 5: Verificar .env da VPS"
# ================================================================

if [ ! -f "$ENV_FILE" ]; then
    warn ".env nao encontrado em $ENV_FILE"
    cat > "$ENV_FILE" << 'ENV_TEMPLATE'
# PortalCursos.NG - .env da VPS
# EDITE ESTE ARQUIVO com os valores reais

SPRING_DATASOURCE_URL=jdbc:postgresql://SEU_HOST_NEON/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=neondb_owner
SPRING_DATASOURCE_PASSWORD=SENHA_NEON_AQUI

APP_JWT_SECRET=GERE_COM_openssl_rand_-base64_64
APP_JWT_EXPIRATION=900000
APP_JWT_REFRESH_EXPIRATION=86400000

APP_ROOT_PASSWORD=SENHA_ROOTMASTER_MINIMO_12_CHARS
APP_ADMIN_PASSWORD=SENHA_ADMIN_MINIMO_12_CHARS

CORS_ALLOWED_ORIGINS=https://xavierbr-vps.tech,https://www.xavierbr-vps.tech
APP_SECURE_COOKIES=true

NEXT_PUBLIC_API_URL=https://xavierbr-vps.tech
NEXT_PUBLIC_BASE_PATH=/portalcursos.ng

DOMAIN_NAME=xavierbr-vps.tech
EMAIL_ADDRESS=claudio.xavier@gmail.com

PORT=8080
ENV_TEMPLATE
    fail ".env criado em $ENV_FILE — preencha os valores e execute novamente."
fi
ok ".env encontrado: $ENV_FILE"

# Valida variaveis criticas
MISSING_VARS=()
for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL CORS_ALLOWED_ORIGINS DOMAIN_NAME; do
    VAL=$(grep "^${VAR}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '\r' || true)
    if [ -z "$VAL" ] || echo "$VAL" | grep -qE "AQUI|SEU_|GERE_|template"; then
        MISSING_VARS+=("$VAR")
    fi
done

if [ "${#MISSING_VARS[@]}" -gt 0 ]; then
    warn "Variaveis nao configuradas no .env:"
    for V in "${MISSING_VARS[@]}"; do warn "  - $V"; done
    fail "Preencha as variaveis acima em $ENV_FILE e execute novamente."
fi
ok "Variaveis do .env validadas."

# Garante DOMAIN_NAME no .env (necessario para labels Traefik)
DOMAIN_IN_ENV=$(grep "^DOMAIN_NAME=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '\r' || echo "")
if [ -z "$DOMAIN_IN_ENV" ]; then
    echo "DOMAIN_NAME=xavierbr-vps.tech" >> "$ENV_FILE"
    log "DOMAIN_NAME adicionado ao .env."
fi

# ================================================================
section "FASE 6: Rebuildar e subir containers"
# ================================================================

log "Parando containers existentes..."
$COMPOSE down --remove-orphans 2>&1 | tail -3 | sed 's/^/  /' || true

log "Removendo imagens antigas (rebuild limpo)..."
docker image rm portalcursos_frontend portalcursos_backend 2>/dev/null \
    | sed 's/^/  /' || true

log "Subindo Postgres..."
$COMPOSE up -d postgres

log "Aguardando Postgres ficar saudavel (max 40s)..."
for i in $(seq 1 20); do
    PG_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' \
        portalcursos_postgres 2>/dev/null || echo "starting")
    if [ "$PG_HEALTH" = "healthy" ]; then
        ok "Postgres saudavel."
        break
    fi
    if [ "$i" -eq 20 ]; then
        warn "Postgres nao ficou saudavel em 40s. Continuando..."
        docker logs portalcursos_postgres --tail 10 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

log "Buildando e subindo Backend..."
$COMPOSE up -d --build backend 2>&1 | tail -5 | sed 's/^/  /'

log "Buildando e subindo Frontend..."
$COMPOSE up -d --build frontend 2>&1 | tail -5 | sed 's/^/  /'

# ================================================================
section "FASE 7: Aguardar servicos responderem (portas locais)"
# ================================================================

log "Aguardando Backend na porta 8090 (max 90s)..."
BE_UP=false
for i in $(seq 1 45); do
    if curl -sf http://127.0.0.1:8090/api/health > /dev/null 2>&1; then
        ok "Backend respondendo em http://127.0.0.1:8090"
        BE_UP=true
        break
    fi
    if [ "$i" -eq 45 ]; then
        warn "Backend nao respondeu em 90s. Logs:"
        docker logs portalcursos_backend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

log "Aguardando Frontend na porta 3010 (max 120s)..."
FE_UP=false
for i in $(seq 1 60); do
    CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010 2>/dev/null || echo "000")
    if [ "$CODE" != "000" ]; then
        ok "Frontend respondendo em http://127.0.0.1:3010 (HTTP $CODE)"
        FE_UP=true
        break
    fi
    if [ "$i" -eq 60 ]; then
        warn "Frontend nao respondeu em 120s. Logs:"
        docker logs portalcursos_frontend --tail 30 2>&1 | sed 's/^/  /'
    fi
    sleep 2
done

# ================================================================
section "FASE 8: Confirmar containers na rede easypanel"
# ================================================================

for CTR in portalcursos_backend portalcursos_frontend; do
    if ! docker inspect "$CTR" &>/dev/null; then
        warn "Container $CTR nao encontrado."
        continue
    fi
    IN_NET=$(docker inspect "$CTR" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null \
        | tr ' ' '\n' | grep -c "^easypanel$" || echo "0")
    if [ "$IN_NET" -eq 0 ]; then
        docker network connect easypanel "$CTR" 2>/dev/null \
            && ok "$CTR conectado a rede easypanel." \
            || warn "Nao foi possivel conectar $CTR (pode ja estar conectado)."
    else
        ok "$CTR ja esta na rede easypanel."
    fi
done

# ================================================================
section "FASE 9: Recarregar Traefik"
# ================================================================

TRAEFIK_ID=$(docker ps --filter name=easypanel-traefik --filter status=running -q 2>/dev/null | head -1 || true)
if [ -n "$TRAEFIK_ID" ]; then
    docker kill --signal=SIGHUP "$TRAEFIK_ID" 2>/dev/null \
        && ok "Traefik recarregado (SIGHUP)." \
        || warn "Nao foi possivel enviar SIGHUP ao Traefik."
fi

# Aguarda Traefik propagar as rotas
sleep 5

# ================================================================
section "FASE 10: Validacao final via dominio"
# ================================================================

FE_LOCAL=$(curl -sf -o /dev/null -w "%{http_code}" \
    http://127.0.0.1:3010/portalcursos.ng 2>/dev/null || echo "000")
BE_LOCAL=$(curl -sf -o /dev/null -w "%{http_code}" \
    http://127.0.0.1:8090/api/health 2>/dev/null || echo "000")

FE_HTTPS=$(curl -sk -o /dev/null -w "%{http_code}" \
    "https://${DOMAIN}/portalcursos.ng" 2>/dev/null || echo "000")
BE_HTTPS=$(curl -sk -o /dev/null -w "%{http_code}" \
    "https://${DOMAIN}/api/health" 2>/dev/null || echo "000")

echo ""
echo "  Containers:"
docker ps --filter "name=portalcursos" \
    --format "    {{.Names}}: {{.Status}}" 2>/dev/null || true

echo ""
echo "  Portas locais (direto no host):"
echo "    Frontend http://127.0.0.1:3010/portalcursos.ng : HTTP $FE_LOCAL"
echo "    Backend  http://127.0.0.1:8090/api/health      : HTTP $BE_LOCAL"

echo ""
echo "  Via Traefik/dominio:"
echo "    Frontend https://${DOMAIN}/portalcursos.ng : HTTP $FE_HTTPS"
echo "    Backend  https://${DOMAIN}/api/health      : HTTP $BE_HTTPS"
echo ""

# ── Avalia resultado ─────────────────────────────────────────────
LOCAL_OK=false
DOMAIN_OK=false

[ "$FE_LOCAL" != "000" ] && [ "$BE_LOCAL" != "000" ] && LOCAL_OK=true
[ "$FE_HTTPS" != "000" ] && [ "$BE_HTTPS" != "000" ] && DOMAIN_OK=true

echo "=================================================================="

if $LOCAL_OK && $DOMAIN_OK; then
    ok "TUDO OK — Portal acessivel via Traefik com HTTPS."
    echo ""
    echo "  Acesse: https://${DOMAIN}/portalcursos.ng"
    echo "=================================================================="
    exit 0
fi

if $LOCAL_OK && ! $DOMAIN_OK; then
    warn "Containers OK localmente, mas Traefik ainda nao roteou."
    echo ""
    echo "  Aguarde 30-60s e tente acessar:"
    echo "  https://${DOMAIN}/portalcursos.ng"
    echo ""
    echo "  Se ainda falhar, verifique as labels no EasyPanel:"
    echo "    docker inspect portalcursos_frontend | grep -i traefik"
    echo "    docker inspect portalcursos_backend  | grep -i traefik"
    echo ""
    echo "  Verifique os logs do Traefik:"
    echo "    docker logs easypanel-traefik.1.5sme8r3s80fvxzdf7rkpttmfb --tail 50"
    echo "=================================================================="
    # Saida 0: containers OK, Traefik pode demorar para propagar
    exit 0
fi

# Falha: containers nao responderam localmente
warn "Um ou mais containers nao responderam."
echo ""
echo "  Diagnostico:"
echo "    docker ps -a --filter name=portalcursos"
echo "    docker logs portalcursos_frontend --tail 50"
echo "    docker logs portalcursos_backend  --tail 50"
echo "    docker logs portalcursos_postgres --tail 20"
echo "=================================================================="
exit 1
