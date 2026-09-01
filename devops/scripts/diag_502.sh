#!/bin/bash
# diag_502.sh - Diagnostico preciso do 502 Bad Gateway
# PortalCursos.NG | xavierbr-vps.tech
# Executar na VPS: sudo bash /tmp/diag_502.sh

echo "=================================================================="
echo "  DIAGNOSTICO 502 - PortalCursos.NG"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "=================================================================="

echo ""
echo "=== 1. CONTAINERS PORTALCURSOS (status) ==="
docker ps -a --filter name=portalcursos \
    --format "{{.Names}}: {{.Status}} | portas: {{.Ports}}" 2>/dev/null \
    || echo "(docker indisponivel)"

echo ""
echo "=== 2. TRAEFIK ATIVO ==="
docker ps --filter name=easypanel-traefik \
    --format "{{.Names}}: {{.Status}} | {{.Ports}}" 2>/dev/null \
    || echo "(traefik nao encontrado)"

echo ""
echo "=== 3. CONTAINERS NA REDE easypanel ==="
docker network inspect easypanel \
    --format '{{range $id,$c := .Containers}}  {{$c.Name}}{{"\n"}}{{end}}' 2>/dev/null \
    || echo "(rede easypanel nao encontrada)"

echo ""
echo "=== 4. LABELS TRAEFIK NO FRONTEND ==="
docker inspect portalcursos_frontend \
    --format '{{range $k,$v := .Config.Labels}}{{if contains $k "traefik"}}  {{$k}}={{$v}}{{"\n"}}{{end}}{{end}}' 2>/dev/null \
    || echo "(container frontend nao encontrado)"

echo ""
echo "=== 5. HEALTH LOCAL BACKEND (porta 8090) ==="
curl -sf http://127.0.0.1:8090/api/health 2>/dev/null && echo "" \
    || echo "NAO RESPONDEU em 127.0.0.1:8090"

echo ""
echo "=== 6. HEALTH LOCAL FRONTEND (porta 3010) ==="
CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010 2>/dev/null || echo "000")
echo "HTTP $CODE em 127.0.0.1:3010"

echo ""
echo "=== 7. LOGS BACKEND (ultimas 30 linhas) ==="
docker logs portalcursos_backend --tail 30 2>&1 || echo "(sem logs)"

echo ""
echo "=== 8. LOGS FRONTEND (ultimas 20 linhas) ==="
docker logs portalcursos_frontend --tail 20 2>&1 || echo "(sem logs)"

echo ""
echo "=== 9. LOGS TRAEFIK (ultimas 20 linhas) ==="
TRAEFIK_ID=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null | head -1)
[ -n "$TRAEFIK_ID" ] && docker logs "$TRAEFIK_ID" --tail 20 2>&1 || echo "(sem logs traefik)"

echo ""
echo "=== 10. ARQUIVO .env EXISTE E TEM VARIAVEIS CRITICAS ==="
ENV_FILE="/var/www/portalcursos/.env"
if [ -f "$ENV_FILE" ]; then
    echo "  .env encontrado"
    for VAR in APP_JWT_SECRET SPRING_DATASOURCE_URL CORS_ALLOWED_ORIGINS DOMAIN_NAME; do
        VAL=$(grep "^${VAR}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '\r' | head -c 20)
        if [ -n "$VAL" ]; then
            echo "  $VAR = ${VAL}..."
        else
            echo "  $VAR = [AUSENTE OU VAZIO]"
        fi
    done
else
    echo "  .env NAO ENCONTRADO em $ENV_FILE"
fi

echo ""
echo "=== 11. DOCKER COMPOSE FILE ==="
COMPOSE_FILE="/var/www/portalcursos/devops/docker-compose.prod.yml"
if [ -f "$COMPOSE_FILE" ]; then
    echo "  Encontrado. Linhas de labels traefik:"
    grep -c "traefik" "$COMPOSE_FILE" 2>/dev/null | xargs -I{} echo "  {} ocorrencias de traefik no compose"
    echo "  Primeira linha do arquivo:"
    head -1 "$COMPOSE_FILE"
    echo "  Hash do arquivo:"
    md5sum "$COMPOSE_FILE" 2>/dev/null || sha256sum "$COMPOSE_FILE" 2>/dev/null
else
    echo "  docker-compose.prod.yml NAO ENCONTRADO"
fi

echo ""
echo "=== 12. GIT STATUS NA VPS ==="
cd /var/www/portalcursos 2>/dev/null && git log --oneline -3 2>/dev/null || echo "(nao e repositorio git)"

echo ""
echo "=================================================================="
echo "  FIM DO DIAGNOSTICO"
echo "=================================================================="
