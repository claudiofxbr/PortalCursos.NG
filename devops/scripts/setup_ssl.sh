#!/bin/bash

# Script de Configuração SSL (Let's Encrypt / Certbot) - PortalCursos.NG
# Executado REMOTAMENTE na VPS via SSH — não rodar no Windows PowerShell.

set -e

# Lê domínio e e-mail do .env da raiz do projeto (se existir)
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

DOMAIN="${DOMAIN_NAME:-}"
EMAIL="${EMAIL_ADDRESS:-}"

# Tenta ler do .env se não veio por variável de ambiente
if [ -z "$DOMAIN" ] && [ -f "$PROJECT_ROOT/.env" ]; then
    DOMAIN=$(grep '^DOMAIN_NAME=' "$PROJECT_ROOT/.env" | cut -d= -f2 | tr -d '\r ')
fi
if [ -z "$EMAIL" ] && [ -f "$PROJECT_ROOT/.env" ]; then
    EMAIL=$(grep '^EMAIL_ADDRESS=' "$PROJECT_ROOT/.env" | cut -d= -f2 | tr -d '\r ')
fi

# Fallbacks seguros
DOMAIN="${DOMAIN:-xavierbr-vps.tech}"
EMAIL="${EMAIL:-claudiofxbr@gmail.com}"
WWW_DOMAIN="www.$DOMAIN"

echo "----------------------------------------------------"
echo "Iniciando Configuração SSL para: $DOMAIN"
echo "E-mail para notificações: $EMAIL"
echo "----------------------------------------------------"

# 1. Instalar Certbot e Plugin Nginx
echo "Verificando dependências..."
sudo apt-get update -qq
sudo apt-get install -y certbot python3-certbot-nginx

# 2. Abrir portas no Firewall
if command -v ufw &>/dev/null; then
    echo "Configurando Firewall (UFW)..."
    sudo ufw allow 'Nginx Full' || true
fi

# 3. Validar que o Nginx está rodando antes de pedir certificado
if ! sudo systemctl is-active --quiet nginx; then
    echo "Nginx não está ativo. Tentando iniciar..."
    sudo systemctl start nginx || {
        echo "ERRO: Nginx não pôde ser iniciado. Verifique com: sudo nginx -t"
        exit 1
    }
fi

# 4. Verificar se já existe certificado válido
if sudo certbot certificates 2>/dev/null | grep -q "$DOMAIN"; then
    echo "Certificado já existe para $DOMAIN. Verificando validade..."
    sudo certbot renew --nginx --cert-name "$DOMAIN" --non-interactive 2>&1 || true
    echo "Certificado verificado/renovado."
else
    # 5. Emitir novo certificado
    echo "Emitindo novo certificado para $DOMAIN e $WWW_DOMAIN..."
    sudo certbot --nginx \
        -d "$DOMAIN" \
        -d "$WWW_DOMAIN" \
        --agree-tos \
        -m "$EMAIL" \
        --non-interactive \
        --redirect || {
        echo ""
        echo "ERRO: Certbot falhou. Causas comuns:"
        echo "  1. O DNS A de '$DOMAIN' não aponta para o IP desta VPS."
        echo "  2. A porta 80 está bloqueada ou ocupada por outro processo."
        echo "  3. Rate limit do Let's Encrypt (máx 5 emissões por domínio/semana)."
        echo ""
        echo "Para diagnosticar: sudo certbot --nginx -d $DOMAIN --dry-run"
        exit 1
    }
fi

# 6. Ajustar permissões dos certificados
sudo chmod -R 755 /etc/letsencrypt/archive/ 2>/dev/null || true
sudo chmod -R 755 /etc/letsencrypt/live/    2>/dev/null || true

# 7. Configurar renovação automática via cron (se não existir)
if ! crontab -l 2>/dev/null | grep -q 'certbot renew'; then
    echo "Configurando renovação automática via cron..."
    (crontab -l 2>/dev/null; echo "0 3 * * * sudo certbot renew --nginx --quiet && sudo systemctl reload nginx") | crontab -
    echo "Cron de renovação configurado (diariamente às 3h)."
fi

# 8. Reload do Nginx
sudo systemctl reload nginx

echo "----------------------------------------------------"
echo "SSL configurado com sucesso!"
echo "Acesse: https://$DOMAIN"
echo "----------------------------------------------------"
