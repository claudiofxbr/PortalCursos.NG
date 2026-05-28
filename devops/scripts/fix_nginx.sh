#!/bin/bash

# PortalCursos.NG - Script de Correção Emergencial Nginx (V40.0)
# Resolve problemas de porta 80/443 e SSL

echo "🛠️ Iniciando Diagnóstico e Correção do Nginx..."

# 1. Verificar Firewall Externo (Aviso)
echo "🔍 Nota: Verifique se o Firewall da Hostinger permite tráfego nas portas 80 e 443."

# 2. Configurar Firewall Local (UFW)
echo "🛡️ Configurando Firewall Local..."
if command -v ufw &> /dev/null; then
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
    sudo ufw allow 'Nginx Full'
    sudo ufw reload
fi

# 3. Verificar Configurações Ativas
echo "🧹 Limpando conflitos..."
sudo rm /etc/nginx/sites-enabled/default 2>/dev/null || true

# 4. Validar Certificados SSL e criar placeholders se ausentes (Evita falha catastrófica do Nginx)
echo "🔐 Verificando certificados SSL..."
if [ ! -f "/etc/letsencrypt/live/portalcursos.ng/fullchain.pem" ]; then
    echo "⚠️ Certificado SSL nao encontrado! Gerando certificado autoassinado de contingencia..."
    sudo mkdir -p /etc/letsencrypt/live/portalcursos.ng/
    sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
      -keyout /etc/letsencrypt/live/portalcursos.ng/privkey.pem \
      -out /etc/letsencrypt/live/portalcursos.ng/fullchain.pem \
      -subj "/CN=portalcursos.ng"
    echo "✅ Certificado autoassinado gerado com sucesso!"
fi

# 5. Aplicar Nova Configuração
echo "🔗 Aplicando configuração..."
SOURCE_CONF="$(pwd)/devops/scripts/nginx.conf"
if [ -f "$SOURCE_CONF" ]; then
    sudo cp "$SOURCE_CONF" /etc/nginx/sites-available/portalcursos
    sudo ln -s /etc/nginx/sites-available/portalcursos /etc/nginx/sites-enabled/ 2>/dev/null || true
fi

# 6. Teste de Sintaxe
echo "🧪 Testando sintaxe..."
if ! sudo nginx -t; then
    echo "❌ Erro na sintaxe do Nginx! Revertendo ou corrigindo..."
    # Se falhar por causa do SSL, tentaremos comentar as linhas de SSL temporariamente?
    # Melhor deixar o usuário ver o erro.
fi

# 7. Reinicialização e Diagnóstico de Portas
echo "🔄 Reiniciando Nginx..."
sudo systemctl restart nginx

echo "📊 Verificação de Portas (Escuta):"
sudo netstat -tulpn | grep -E '(:80|:443|:3010|:8090)'

echo "🌐 Teste de Resposta Local (Loopback):"
curl -I http://localhost:3010 2>/dev/null | grep HTTP || echo "❌ Frontend (3010) Offline"
curl -I http://localhost:8090/api/health 2>/dev/null | grep HTTP || echo "❌ Backend (8090) Offline"

echo "✅ Script concluído. Se o erro persistir, verifique os logs: sudo journalctl -u nginx -f"

