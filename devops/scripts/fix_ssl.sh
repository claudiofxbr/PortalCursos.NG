#!/bin/bash
# =================================================================
# fix_ssl.sh - Verifica/ativa SSL via Traefik + Let's Encrypt
# PortalCursos.NG | xavierbr-vps.tech
#
# Com EasyPanel/Traefik, o SSL e gerenciado automaticamente
# pelo certresolver 'letsencrypt' configurado nas labels Docker.
# Este script verifica se o certificado foi emitido e diagnostica
# problemas caso o HTTPS ainda nao esteja funcionando.
#
# Executar NA VPS como root:
#   sudo bash /tmp/fix_ssl.sh
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
    fail "Execute como root: sudo bash fix_ssl.sh"
fi

DOMAIN="xavierbr-vps.tech"

echo ""
echo "=================================================================="
echo "  FIX SSL - PortalCursos.NG | ${DOMAIN}"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Modo: Traefik/EasyPanel (certresolver letsencrypt)"
echo "=================================================================="
echo ""

# ================================================================
section "FASE 1: Verificar Traefik ativo"
# ================================================================

TRAEFIK_ID=$(docker ps --filter name=easypanel-traefik --filter status=running -q 2>/dev/null | head -1 || true)
if [ -z "$TRAEFIK_ID" ]; then
    fail "Traefik nao esta rodando. Verifique o EasyPanel."
fi
ok "Traefik ativo: $TRAEFIK_ID"

# ================================================================
section "FASE 2: Verificar containers do portal ativos"
# ================================================================

for CTR in portalcursos_frontend portalcursos_backend; do
    STATUS=$(docker inspect "$CTR" --format='{{.State.Status}}' 2>/dev/null || echo "nao encontrado")
    if [ "$STATUS" != "running" ]; then
        warn "$CTR status: $STATUS"
        warn "Execute fix_502.sh primeiro para subir os containers."
    else
        ok "$CTR rodando."
    fi
done

# ================================================================
section "FASE 3: Verificar labels Traefik nos containers"
# ================================================================

for CTR in portalcursos_frontend portalcursos_backend; do
    LABELS=$(docker inspect "$CTR" \
        --format '{{range $k,$v := .Config.Labels}}{{$k}}={{$v}}{{"\n"}}{{end}}' 2>/dev/null \
        | grep "traefik" | wc -l || echo "0")
    if [ "$LABELS" -gt 0 ]; then
        ok "$CTR tem ${LABELS} labels Traefik configuradas."
    else
        warn "$CTR NAO tem labels Traefik. Execute fix_502.sh para redeployar com o docker-compose atualizado."
    fi
done

# ================================================================
section "FASE 4: Verificar acesso HTTPS"
# ================================================================

log "Testando https://${DOMAIN}/portalcursos.ng ..."
HTTPS_CODE=$(curl -sk -o /dev/null -w "%{http_code}" \
    "https://${DOMAIN}/portalcursos.ng" 2>/dev/null || echo "000")
log "Codigo HTTP: $HTTPS_CODE"

log "Testando certificado SSL..."
CERT_INFO=$(echo | openssl s_client -connect "${DOMAIN}:443" -servername "${DOMAIN}" \
    -brief 2>/dev/null | head -10 || echo "erro na conexao SSL")
echo "$CERT_INFO" | sed 's/^/  /'

ISSUER=$(echo | openssl s_client -connect "${DOMAIN}:443" -servername "${DOMAIN}" 2>/dev/null \
    | openssl x509 -noout -issuer 2>/dev/null || echo "nao disponivel")
EXPIRY=$(echo | openssl s_client -connect "${DOMAIN}:443" -servername "${DOMAIN}" 2>/dev/null \
    | openssl x509 -noout -enddate 2>/dev/null || echo "nao disponivel")

echo ""
echo "  Emissor : $ISSUER"
echo "  Expira  : $EXPIRY"
echo ""

# ================================================================
section "FASE 5: Verificar acervo de certificados do Traefik"
# ================================================================

ACME_PATHS=(
    "/var/lib/easypanel/certs/acme.json"
    "/etc/traefik/acme.json"
    "/letsencrypt/acme.json"
)

ACME_FOUND=false
for ACME_PATH in "${ACME_PATHS[@]}"; do
    if [ -f "$ACME_PATH" ]; then
        ok "acme.json encontrado: $ACME_PATH"
        DOMAINS_IN_ACME=$(python3 -c "
import json
try:
    data = json.load(open('${ACME_PATH}'))
    certs = []
    for resolver in data.values():
        for cert in resolver.get('Certificates', []):
            d = cert.get('domain', {})
            main = d.get('main', '')
            if main:
                certs.append(main)
    print('\n'.join(certs) if certs else 'nenhum certificado encontrado')
except Exception as e:
    print('erro ao ler: ' + str(e))
" 2>/dev/null || echo "python3 nao disponivel")
        echo "  Dominios no acme.json:"
        echo "$DOMAINS_IN_ACME" | sed 's/^/    /'
        ACME_FOUND=true
        break
    fi
done

if ! $ACME_FOUND; then
    log "acme.json nao encontrado nos caminhos padrao. Verificando no container Traefik..."
    docker exec "$TRAEFIK_ID" find / -name "acme.json" 2>/dev/null \
        | head -5 | sed 's/^/  /' || warn "Nao foi possivel inspecionar o container Traefik."
fi

# ================================================================
section "FASE 6: Verificar porta 80 acessivel (ACME challenge)"
# ================================================================

log "Testando se porta 80 esta acessivel externamente..."
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --max-time 10 \
    "http://${DOMAIN}/" 2>/dev/null || echo "000")
echo "  HTTP (porta 80) resposta: $HTTP_CODE"

if [ "$HTTP_CODE" = "000" ]; then
    warn "Porta 80 nao respondeu."
    warn "O Traefik precisa de acesso externo na porta 80 para o ACME HTTP-01 challenge."
    warn "Verifique se o firewall da Hostinger permite a porta 80."
else
    ok "Porta 80 acessivel (HTTP $HTTP_CODE)."
fi

# ================================================================
section "RESULTADO"
# ================================================================

echo ""

if echo "$ISSUER" | grep -qi "let.s encrypt"; then
    echo "=================================================================="
    ok "SSL OK - Certificado Let's Encrypt valido!"
    echo ""
    echo "  Acesse: https://${DOMAIN}/portalcursos.ng"
    echo ""
    echo "  Validacao externa:"
    echo "  https://www.ssllabs.com/ssltest/analyze.html?d=${DOMAIN}"
    echo "=================================================================="
    exit 0
elif [ "$HTTPS_CODE" != "000" ]; then
    echo "=================================================================="
    warn "HTTPS funcionando mas certificado ainda nao e do Let's Encrypt."
    echo ""
    echo "  O Traefik emite o certificado automaticamente no primeiro acesso."
    echo "  Aguarde 1-2 minutos e acesse novamente:"
    echo "  https://${DOMAIN}/portalcursos.ng"
    echo ""
    echo "  Se ERR_CERT_AUTHORITY_INVALID persistir apos 5 minutos:"
    echo "  1. Logs do Traefik:"
    echo "     docker logs $TRAEFIK_ID --tail 100 | grep -i cert"
    echo "  2. Confirme porta 80 aberta no firewall da Hostinger"
    echo "  3. Confirme DNS aponta para esta VPS:"
    echo "     curl -s https://api64.ipify.org"
    echo "     nslookup ${DOMAIN}"
    echo "=================================================================="
    exit 0
else
    echo "=================================================================="
    warn "HTTPS nao respondeu. Diagnostico:"
    echo ""
    echo "  1. Logs do Traefik:"
    echo "     docker logs $TRAEFIK_ID --tail 100"
    echo ""
    echo "  2. Containers na rede easypanel:"
    echo "     docker inspect portalcursos_frontend | grep -A5 easypanel"
    echo ""
    echo "  3. Labels Traefik aplicadas:"
    echo "     docker inspect portalcursos_frontend | grep -i traefik"
    echo ""
    echo "  4. DNS:"
    echo "     curl -s https://api64.ipify.org"
    echo "     nslookup ${DOMAIN}"
    echo "=================================================================="
    exit 1
fi
