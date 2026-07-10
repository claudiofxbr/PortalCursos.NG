#!/bin/bash
# =================================================================
# setup_server.sh — Prepara a VPS Hostinger para o PortalCursos.NG
# Stack: Docker + Docker Compose v2 + Git
# Destino: Ubuntu 22.04 / Debian 12 (Hostinger VPS)
#
# Execute UMA VEZ na VPS antes do primeiro deploy:
#   ssh root@SEU_IP "bash /tmp/setup_server.sh"
#
# NAO instala Java, Node, Nginx ou pm2 — tudo roda em containers Docker.
# O Traefik e gerenciado pelo EasyPanel.
# =================================================================
set -euo pipefail

ok()   { echo "[OK]    $*"; }
info() { echo "[INFO]  $*"; }
fail() { echo "[ERRO]  $*" >&2; exit 1; }

echo ""
echo "============================================"
echo "  PortalCursos.NG — Setup da VPS"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Servidor: $(hostname)"
echo "============================================"
echo ""

# ── 1. Verificar sistema ──────────────────────────────────────────
[ "$(id -u)" -eq 0 ] || fail "Execute como root: sudo bash setup_server.sh"

if ! command -v apt-get > /dev/null 2>&1; then
    fail "Este script requer Ubuntu/Debian (apt-get nao encontrado)"
fi
ok "Sistema compativel"

# ── 2. Atualizar pacotes base ─────────────────────────────────────
info "Atualizando pacotes do sistema..."
apt-get update -qq
apt-get install -y -qq \
    ca-certificates \
    curl \
    gnupg \
    git \
    wget \
    unzip \
    lsb-release
ok "Pacotes base instalados"

# ── 3. Instalar Docker Engine ─────────────────────────────────────
if command -v docker > /dev/null 2>&1; then
    ok "Docker ja instalado: $(docker --version)"
else
    info "Instalando Docker Engine..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/ubuntu \
        $(lsb_release -cs) stable" \
        | tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    systemctl enable docker
    systemctl start docker
    ok "Docker instalado: $(docker --version)"
fi

# ── 4. Verificar Docker Compose v2 ───────────────────────────────
if docker compose version > /dev/null 2>&1; then
    ok "Docker Compose: $(docker compose version)"
else
    fail "Docker Compose plugin nao encontrado. Reinstale o docker-compose-plugin."
fi

# ── 5. Configurar projeto ─────────────────────────────────────────
info "Criando diretorio do projeto..."
mkdir -p /var/www/portalcursos
ok "Diretorio: /var/www/portalcursos"

# ── 6. Firewall (ufw) ─────────────────────────────────────────────
if command -v ufw > /dev/null 2>&1; then
    info "Configurando firewall UFW..."
    ufw allow 22/tcp   comment "SSH"
    ufw allow 80/tcp   comment "HTTP (Traefik)"
    ufw allow 443/tcp  comment "HTTPS (Traefik)"
    # Portas internas dos containers — acessiveis apenas via 127.0.0.1
    ufw deny 8080/tcp  comment "Backend — acesse via Traefik"
    ufw deny 3000/tcp  comment "Frontend — acesse via Traefik"
    ufw --force enable
    ok "Firewall configurado"
else
    info "UFW nao encontrado — firewall ignorado (configure manualmente)"
fi

# ── 7. Resumo final ───────────────────────────────────────────────
echo ""
echo "============================================"
echo "  Setup concluido!"
echo ""
echo "  Proximos passos:"
echo "  1. Crie /var/www/portalcursos/.env com:"
echo "       APP_JWT_SECRET=<min-64-chars>"
echo "       SPRING_DATASOURCE_URL=jdbc:postgresql://..."
echo "       SPRING_DATASOURCE_USERNAME=..."
echo "       SPRING_DATASOURCE_PASSWORD=..."
echo "       CORS_ALLOWED_ORIGINS=https://xavierbr-vps.tech"
echo "       DOMAIN_NAME=xavierbr-vps.tech"
echo "       NEXT_PUBLIC_API_URL=https://xavierbr-vps.tech"
echo "       NEXT_PUBLIC_BASE_PATH=/portalcursos.ng"
echo "       APP_SECURE_COOKIES=true"
echo "       PORTAL_ACCESS_CODE=<codigo>"
echo "       APP_ROOT_PASSWORD=<senha-root>"
echo "       APP_ADMIN_PASSWORD=<senha-admin>"
echo ""
echo "  2. Execute o deploy:"
echo "       bash /tmp/deploy_vps.sh"
echo ""
echo "  3. Acesse: https://xavierbr-vps.tech/portalcursos.ng"
echo "============================================"
