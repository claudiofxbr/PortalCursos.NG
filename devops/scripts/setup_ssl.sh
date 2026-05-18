#!/bin/bash

# Script para Configuração de SSL (HTTPS) via Certbot - PortalCursos.NG (V40.0)
# Autor: Antigravity AI

set -e

DOMAIN="portalcursos.ng"
WWW_DOMAIN="www.portalcursos.ng"
EMAIL="claudiofxbr@gmail.com"

echo "----------------------------------------------------"
echo "🔐 Iniciando Configuração SSL para $DOMAIN"
echo "----------------------------------------------------"

# 1. Instalar Certbot e Plugin Nginx se não existirem
echo "📦 Verificando dependências..."
sudo apt update
sudo apt install -y certbot python3-certbot-nginx net-tools

# 2. Abrir portas no Firewall (se houver ufw)
if command -v ufw &> /dev/null; then
    echo "🛡️ Configurando Firewall (UFW)..."
    sudo ufw allow 'Nginx Full'
fi

# 3. Validar se a porta 80 está disponível para o desafio ACME
if sudo lsof -i :80 | grep -q LISTEN; then
    echo "⚠️ Porta 80 ocupada. O Certbot tentará usar o plugin Nginx..."
fi

# 4. Solicitar Certificado
echo "📜 Solicitando certificado para $DOMAIN e $WWW_DOMAIN..."
# Usamos --nginx para que o certbot entenda a configuração atual e adicione o SSL
sudo certbot --nginx -d $DOMAIN -d $WWW_DOMAIN --agree-tos -m $EMAIL --non-interactive --redirect || {
    echo "❌ Erro ao obter certificado. Verifique se o DNS está apontado para este IP."
    exit 1
}

# 5. Garantir Permissões
echo "🔑 Ajustando permissões dos certificados..."
sudo chmod -R 755 /etc/letsencrypt/archive/
sudo chmod -R 755 /etc/letsencrypt/live/

# 6. Reiniciar Nginx
echo "🔄 Reiniciando Nginx para aplicar SSL..."
sudo systemctl restart nginx

# 7. Verificar Renovação Automática
echo "🔄 Verificando renovação automática..."
sudo certbot renew --dry-run

echo "----------------------------------------------------"
echo "✅ SSL configurado com sucesso!"
echo "🌐 Acesse: https://$DOMAIN"
echo "----------------------------------------------------"

