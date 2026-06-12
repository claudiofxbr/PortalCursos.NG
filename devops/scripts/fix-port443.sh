#!/bin/bash
# =================================================================
# fix-port443.sh — Diagnóstico e Liberação das Portas 80/443
# Executado NA VPS via SSH. NÃO rodar no Windows PowerShell.
# =================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'

log()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
ok()   { echo -e "${GREEN}[OK]${NC}    $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERRO]${NC}  $*"; }

echo -e "${CYAN}============================================================${NC}"
echo -e "${CYAN}  DIAGNÓSTICO E LIBERAÇÃO DE PORTAS 80/443 — PortalCursos  ${NC}"
echo -e "${CYAN}============================================================${NC}"
echo ""

# ---------------------------------------------------------------
# FASE 1: Identificar o que está ocupando as portas
# ---------------------------------------------------------------
log "Identificando processos nas portas 80 e 443..."

PORT80_INFO=""
PORT443_INFO=""

if command -v ss &>/dev/null; then
    PORT80_INFO=$(ss -tlnp 'sport = :80'  2>/dev/null | grep -v "^Netid" || true)
    PORT443_INFO=$(ss -tlnp 'sport = :443' 2>/dev/null | grep -v "^Netid" || true)
elif command -v netstat &>/dev/null; then
    PORT80_INFO=$(netstat -tlnp 2>/dev/null | grep ':80 '  || true)
    PORT443_INFO=$(netstat -tlnp 2>/dev/null | grep ':443 ' || true)
fi

echo ""
echo "  Porta 80:"
if [ -n "$PORT80_INFO" ]; then
    echo "$PORT80_INFO" | sed 's/^/    /'
else
    ok "Porta 80 LIVRE"
fi

echo ""
echo "  Porta 443:"
if [ -n "$PORT443_INFO" ]; then
    echo "$PORT443_INFO" | sed 's/^/    /'
else
    ok "Porta 443 LIVRE"
fi
echo ""

# ---------------------------------------------------------------
# FASE 2: Detectar e parar conflitantes conhecidos
# ---------------------------------------------------------------
log "Verificando serviços conflitantes..."

CONFLICTANTES=("apache2" "apache" "lighttpd" "caddy" "haproxy")
PAROU_ALGO=false

for svc in "${CONFLICTANTES[@]}"; do
    if systemctl is-active --quiet "$svc" 2>/dev/null; then
        warn "Serviço '$svc' está ativo e pode estar ocupando 80/443. Parando..."
        sudo systemctl stop    "$svc" 2>/dev/null || true
        sudo systemctl disable "$svc" 2>/dev/null || true
        ok "Serviço '$svc' parado e desativado."
        PAROU_ALGO=true
    fi
done

# ---------------------------------------------------------------
# FASE 3: Detectar containers Docker nas portas 80/443
# ---------------------------------------------------------------
if command -v docker &>/dev/null; then
    log "Verificando containers Docker nas portas 80 e 443..."

    CONFLICTING_80=$(docker ps --format '{{.ID}} {{.Names}} {{.Ports}}' 2>/dev/null \
        | grep -E '0\.0\.0\.0:80->|:::80->' | awk '{print $1}' || true)
    CONFLICTING_443=$(docker ps --format '{{.ID}} {{.Names}} {{.Ports}}' 2>/dev/null \
        | grep -E '0\.0\.0\.0:443->|:::443->' | awk '{print $1}' || true)

    ALL_CONFLICTING=$(echo -e "$CONFLICTING_80\n$CONFLICTING_443" | sort -u | grep -v '^$' || true)

    if [ -n "$ALL_CONFLICTING" ]; then
        warn "Containers Docker ocupando portas 80/443 encontrados:"
        while IFS= read -r cid; do
            CNAME=$(docker inspect --format '{{.Name}}' "$cid" 2>/dev/null | tr -d '/' || echo "$cid")
            warn "  → Container: $CNAME ($cid)"
            # Parar apenas se NÃO for um container do PortalCursos
            if [[ "$CNAME" != *"portalcursos"* ]]; then
                warn "    Parando container conflitante: $CNAME..."
                docker stop "$cid" 2>/dev/null || true
                ok "    Container $CNAME parado."
                PAROU_ALGO=true
            else
                warn "    Container '$CNAME' é do PortalCursos — não será parado."
                warn "    O nginx.conf usa portas internas (3010/8090), não 80/443 direto."
                warn "    O Nginx do HOST é quem usa 80/443 e faz proxy para os containers."
            fi
        done <<< "$ALL_CONFLICTING"
    else
        ok "Nenhum container Docker ocupando 80/443 diretamente."
    fi
fi

# ---------------------------------------------------------------
# FASE 4: Forçar liberação via fuser/kill (último recurso)
# ---------------------------------------------------------------
log "Verificando se algum processo ainda prende as portas..."

PORTA80_PID=$(sudo fuser 80/tcp 2>/dev/null || true)
PORTA443_PID=$(sudo fuser 443/tcp 2>/dev/null || true)

if [ -n "$PORTA443_PID" ]; then
    warn "PIDs na porta 443: $PORTA443_PID"
    # Identificar o processo antes de matar
    for PID in $PORTA443_PID; do
        PNAME=$(ps -p "$PID" -o comm= 2>/dev/null || echo "desconhecido")
        warn "  Processo na porta 443: $PNAME (PID $PID)"
        # Não matar nginx pois iremos reiniciá-lo
        if [[ "$PNAME" != "nginx" ]]; then
            warn "  Encerrando $PNAME (PID $PID)..."
            sudo kill -9 "$PID" 2>/dev/null || true
            PAROU_ALGO=true
        fi
    done
fi

if [ -n "$PORTA80_PID" ]; then
    warn "PIDs na porta 80: $PORTA80_PID"
    for PID in $PORTA80_PID; do
        PNAME=$(ps -p "$PID" -o comm= 2>/dev/null || echo "desconhecido")
        if [[ "$PNAME" != "nginx" ]]; then
            warn "  Encerrando $PNAME (PID $PID)..."
            sudo kill -9 "$PID" 2>/dev/null || true
        fi
    done
fi

# ---------------------------------------------------------------
# FASE 5: Parar o Nginx atual (se estiver em estado inconsistente)
# ---------------------------------------------------------------
log "Reinicializando o Nginx de forma limpa..."
sudo systemctl stop nginx 2>/dev/null || true
sleep 2

# Verificar se ainda há algo nas portas após limpeza
AINDA_443=$(sudo fuser 443/tcp 2>/dev/null || true)
if [ -n "$AINDA_443" ]; then
    err "Ainda há processos na porta 443 após limpeza: $AINDA_443"
    err "PIDs:"
    for PID in $AINDA_443; do
        ps -p "$PID" -o pid,comm,cmd 2>/dev/null | sed 's/^/  /' || true
    done
    echo ""
    err "Ação necessária: identifique e encerre manualmente com 'sudo kill -9 <PID>'"
    exit 1
fi

# ---------------------------------------------------------------
# FASE 6: Testar configuração do Nginx e reiniciar
# ---------------------------------------------------------------
log "Testando configuração do Nginx..."
if sudo nginx -t 2>&1; then
    ok "Configuração do Nginx válida."
    log "Iniciando Nginx..."
    sudo systemctl start nginx
    sleep 1
    if sudo systemctl is-active --quiet nginx; then
        ok "Nginx iniciado com sucesso!"
    else
        err "Nginx falhou ao iniciar. Verifique: sudo journalctl -u nginx -n 30"
        exit 1
    fi
else
    err "Configuração do Nginx tem erros de sintaxe."
    err "Verifique: sudo nginx -t"
    err "Arquivo: sudo cat /etc/nginx/sites-available/portalcursos"
    exit 1
fi

# ---------------------------------------------------------------
# FASE 7: Sumário final
# ---------------------------------------------------------------
echo ""
echo -e "${CYAN}============================================================${NC}"
echo -e "${GREEN}  PORTAS LIBERADAS E NGINX OPERACIONAL${NC}"
echo -e "${CYAN}============================================================${NC}"
sudo ss -tlnp 'sport = :80 or sport = :443' 2>/dev/null | grep -v "^Netid" | sed 's/^/  /' || true
echo ""
ok "Próximo passo: execute a instalação SSL novamente (opção 3 no manage-vps.ps1)."
echo ""
