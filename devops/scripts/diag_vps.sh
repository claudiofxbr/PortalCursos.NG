#!/bin/bash
# diag_vps.sh - Diagnostico completo do 502 (stack Traefik + Docker Compose)
# PortalCursos.NG | xavierbr-vps.tech
# Executar na VPS: sudo bash /tmp/diag_vps.sh

LINE="=================================================================="

echo "$LINE"
echo "  DIAGNOSTICO 502 - PortalCursos.NG"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "$LINE"

# ── 1. Containers portalcursos ────────────────────────────────────
echo ""
echo "=== 1. CONTAINERS PORTALCURSOS ==="
docker ps -a --filter name=portalcursos \
    --format "  {{.Names}}: {{.Status}} | ports: {{.Ports}}" 2>/dev/null \
    || echo "  (docker indisponivel)"

# ── 2. Traefik ────────────────────────────────────────────────────
echo ""
echo "=== 2. TRAEFIK ==="
docker ps -a --filter name=traefik \
    --format "  {{.Names}}: {{.Status}} | ports: {{.Ports}}" 2>/dev/null \
    || echo "  (sem container traefik)"

# ── 3. Rede easypanel ─────────────────────────────────────────────
echo ""
echo "=== 3. REDE easypanel - MEMBROS ==="
docker network inspect easypanel \
    --format '{{range $id,$c := .Containers}}  {{$c.Name}}{{"\n"}}{{end}}' 2>/dev/null \
    || echo "  (rede easypanel NAO EXISTE)"

# ── 4. Backend health interno ─────────────────────────────────────
echo ""
echo "=== 4. BACKEND HEALTH INTERNO (porta 8090) ==="
HEALTH=$(curl -sf http://127.0.0.1:8090/api/health 2>/dev/null)
if [ -n "$HEALTH" ]; then
    echo "  RESPONDEU: $HEALTH"
else
    echo "  NAO RESPONDEU em 127.0.0.1:8090"
fi

# ── 5. Frontend health interno ────────────────────────────────────
echo ""
echo "=== 5. FRONTEND HEALTH INTERNO (porta 3010) ==="
CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010 2>/dev/null || echo "000")
echo "  HTTP $CODE em 127.0.0.1:3010"

# ── 6. Labels Traefik no backend ──────────────────────────────────
echo ""
echo "=== 6. LABELS TRAEFIK - BACKEND ==="
docker inspect portalcursos_backend \
    --format '{{range $k,$v := .Config.Labels}}{{$k}}={{$v}}{{"\n"}}{{end}}' 2>/dev/null \
    | grep traefik | sed 's/^/  /' \
    || echo "  (container nao encontrado)"

# ── 7. Labels Traefik no frontend ─────────────────────────────────
echo ""
echo "=== 7. LABELS TRAEFIK - FRONTEND ==="
docker inspect portalcursos_frontend \
    --format '{{range $k,$v := .Config.Labels}}{{$k}}={{$v}}{{"\n"}}{{end}}' 2>/dev/null \
    | grep traefik | sed 's/^/  /' \
    || echo "  (container nao encontrado)"

# ── 8. Logs backend ───────────────────────────────────────────────
echo ""
echo "=== 8. LOGS BACKEND (ultimas 40 linhas) ==="
docker logs portalcursos_backend --tail 40 2>&1 | sed 's/^/  /' \
    || echo "  (sem container ou sem logs)"

# ── 9. Logs frontend ──────────────────────────────────────────────
echo ""
echo "=== 9. LOGS FRONTEND (ultimas 20 linhas) ==="
docker logs portalcursos_frontend --tail 20 2>&1 | sed 's/^/  /' \
    || echo "  (sem container ou sem logs)"

# ── 10. Logs Traefik ──────────────────────────────────────────────
echo ""
echo "=== 10. LOGS TRAEFIK (ultimas 30 linhas) ==="
TRAEFIK_ID=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null | head -1)
if [ -n "$TRAEFIK_ID" ]; then
    docker logs "$TRAEFIK_ID" --tail 30 2>&1 | sed 's/^/  /'
else
    echo "  (container traefik nao encontrado)"
fi

# ── 11. .env critico ──────────────────────────────────────────────
echo ""
echo "=== 11. VARIAVEIS CRITICAS DO .env ==="
ENV_FILE="/var/www/portalcursos/.env"
if [ -f "$ENV_FILE" ]; then
    echo "  .env encontrado em $ENV_FILE"
    for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME \
               CORS_ALLOWED_ORIGINS DOMAIN_NAME NEXT_PUBLIC_API_URL \
               APP_SECURE_COOKIES NEXT_PUBLIC_BASE_PATH; do
        VAL=$(grep "^${VAR}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '\r')
        if [ -n "$VAL" ]; then
            PREVIEW=$(echo "$VAL" | head -c 30)
            echo "  [OK]     $VAR = ${PREVIEW}..."
        else
            echo "  [FALTA]  $VAR"
        fi
    done
else
    echo "  [ERRO] .env NAO ENCONTRADO em $ENV_FILE"
fi

# ── 12. Git status na VPS ─────────────────────────────────────────
echo ""
echo "=== 12. GIT STATUS NA VPS ==="
cd /var/www/portalcursos 2>/dev/null \
    && git log --oneline -3 2>/dev/null \
    || echo "  (nao e repositorio git ou diretorio nao existe)"

# ── 13. Portas em uso ─────────────────────────────────────────────
echo ""
echo "=== 13. PORTAS 80 / 443 / 8090 / 3010 ==="
ss -tlnp 2>/dev/null | grep -E ':(80|443|8090|3010) ' | sed 's/^/  /' \
    || echo "  (nenhuma dessas portas em uso)"

echo ""
echo "$LINE"
echo "  FIM DO DIAGNOSTICO"
echo "  Cole a saida acima e envie para analise."
echo "$LINE"
