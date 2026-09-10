#!/bin/bash

# PortalCursos.NG - Script de Aplicação Nginx
# Este script deve ser executado na VPS Hostinger

CONF_FILE="/etc/nginx/sites-available/portalcursos"
LINK_FILE="/etc/nginx/sites-enabled/portalcursos"
SOURCE_CONF="$(pwd)/devops/scripts/nginx.conf"
UPSTREAM_SRC="$(pwd)/devops/scripts/portalcursos-upstream.conf"
UPSTREAM_DST="/etc/nginx/conf.d/portalcursos-upstream.conf"

echo "🔧 Aplicando configuração Nginx para portalcursos.ng..."

if [ ! -f "$SOURCE_CONF" ]; then
    echo "❌ Erro: Arquivo de configuração não encontrado em $SOURCE_CONF"
    exit 1
fi

# 0. Instalar o include dos upstreams (portalcursos_api / portalcursos_web).
#    Só cria se não existir — em DEPLOY_STRATEGY=blue-green o deploy_ci.sh é o
#    dono deste arquivo e o reescreve a cada deploy; não sobrescrever aqui.
if [ ! -f "$UPSTREAM_DST" ]; then
    echo "📌 Instalando upstreams do nginx em $UPSTREAM_DST"
    sudo cp "$UPSTREAM_SRC" "$UPSTREAM_DST"
fi

# 1. Copiar para sites-available
sudo cp "$SOURCE_CONF" "$CONF_FILE"

# 2. Criar link simbólico se não existir
if [ ! -L "$LINK_FILE" ]; then
    sudo ln -s "$CONF_FILE" "$LINK_FILE"
fi

# 3. Remover o site padrão do Nginx (evita conflitos na porta 80)
if [ -f "/etc/nginx/sites-enabled/default" ]; then
    echo "🗑️ Removendo configuração padrão do Nginx..."
    sudo rm /etc/nginx/sites-enabled/default
fi

# 4. Abrir portas no Firewall (UFW)
if command -v ufw &> /dev/null; then
    echo "🛡️ Configurando Firewall para permitir tráfego HTTP/HTTPS..."
    sudo ufw allow 'Nginx Full'
fi

# 5. Testar configuração
echo "🧪 Testando configuração do Nginx..."
if sudo nginx -t; then
    echo "✅ Configuração válida. Reiniciando Nginx..."
    sudo systemctl restart nginx
    sudo systemctl enable nginx
    echo "🚀 Nginx atualizado e rodando para portalcursos.ng!"
else
    echo "❌ Erro na configuração do Nginx. Verifique os logs com 'sudo nginx -t' ou 'sudo journalctl -u nginx'."
    exit 1
fi
