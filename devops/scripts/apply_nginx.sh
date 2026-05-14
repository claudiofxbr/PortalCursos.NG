#!/bin/bash

# PortalCursos.NG - Script de Aplicação Nginx
# Este script deve ser executado na VPS Hostinger

CONF_FILE="/etc/nginx/sites-available/portalcursos"
LINK_FILE="/etc/nginx/sites-enabled/portalcursos"
SOURCE_CONF="$(pwd)/devops/scripts/nginx.conf"

echo "🔧 Aplicando configuração Nginx para portalcursos.ng..."

if [ ! -f "$SOURCE_CONF" ]; then
    echo "❌ Erro: Arquivo de configuração não encontrado em $SOURCE_CONF"
    exit 1
fi

# Copiar para sites-available
sudo cp "$SOURCE_CONF" "$CONF_FILE"

# Criar link simbólico se não existir
if [ ! -L "$LINK_FILE" ]; then
    sudo ln -s "$CONF_FILE" "$LINK_FILE"
fi

# Testar configuração
echo "🧪 Testando configuração do Nginx..."
if sudo nginx -t; then
    echo "✅ Configuração válida. Reiniciando Nginx..."
    sudo systemctl restart nginx
    echo "🚀 Nginx atualizado e rodando para portalcursos.ng!"
else
    echo "❌ Erro na configuração do Nginx. Verifique os logs."
    exit 1
fi
