#!/bin/bash

# Script para Configuração de SSL (HTTPS) via Certbot - PortalCursos.NG
# Autor: Antigravity AI

set -e

DOMAIN="portalcursos.ng"
WWW_DOMAIN="www.portalcursos.ng"
EMAIL="claudiofxbr@gmail.com" # Email do proprietário para notificações do Let's Encrypt

echo "----------------------------------------------------"
echo "🔐 Iniciando Configuração SSL para $DOMAIN"
echo "----------------------------------------------------"

# 1. Instalar Certbot e Plugin Nginx se não existirem
if ! command -v certbot &> /dev/null; then
    echo "📦 Instalando Certbot..."
    sudo apt update
    sudo apt install -y certbot python3-certbot-nginx
else
    echo "✅ Certbot já está instalado."
fi

# 2. Abrir portas no Firewall (se houver ufw)
if command -v ufw &> /dev/null; then
    echo "🛡️ Configurando Firewall (UFW)..."
    sudo ufw allow 'Nginx Full'
    sudo ufw delete allow 'Nginx HTTP' || true
fi

# 3. Solicitar Certificado e Configurar Nginx Automaticamente
echo "📜 Solicitando certificado para $DOMAIN e $WWW_DOMAIN..."
# --nginx: usa o plugin nginx para configurar automaticamente
# --non-interactive: evita prompts (exige --agree-tos e -m)
# --agree-tos: aceita os termos
# -m: email para notificações de expiração
# --redirect: configura o redirecionamento de 80 -> 443 automaticamente

sudo certbot --nginx -d $DOMAIN -d $WWW_DOMAIN --agree-tos -m $EMAIL --non-interactive --redirect

# 4. Verificar Renovação Automática
echo "🔄 Verificando renovação automática..."
sudo certbot renew --dry-run

echo "----------------------------------------------------"
echo "✅ SSL configurado com sucesso!"
echo "🌐 Acesse: https://$DOMAIN"
echo "----------------------------------------------------"
