#!/bin/bash

# =================================================================
# OMEGA-SUPREME: Script de Automação Definitiva de Deploy Docker
# PortalCursos.NG - Versão 2026.05 (Docker/Nginx/SSL Edition)
# =================================================================

set -e

# Cores para saída estilizada
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}====================================================${NC}"
echo -e "${BLUE}🚀 PORTALCURSOS.NG - ORQUESTRADOR DEFINITIVO DOCKER${NC}"
echo -e "${BLUE}====================================================${NC}"

# 1. Definir caminhos base
PROJECT_ROOT="$(pwd)"
if [[ "$PROJECT_ROOT" == *"devops/scripts"* ]]; then
    PROJECT_ROOT="$(dirname "$(dirname "$PROJECT_ROOT")")"
fi

DEVOPS_DIR="$PROJECT_ROOT/devops"
SCRIPTS_DIR="$DEVOPS_DIR/scripts"

# 2. Carregar variáveis de ambiente do arquivo .env
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${BLUE}📄 Carregando variáveis de ambiente do .env...${NC}"
    # Carrega variáveis reais exportando-as para o shell
    export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
else
    echo -e "${RED}❌ ERRO: Arquivo .env não encontrado em $PROJECT_ROOT/.env!${NC}"
    echo -e "${RED}Por favor, configure o .env antes de executar este instalador.${NC}"
    exit 1
fi

# 3. Validar se o Docker está instalado, senão instalar
if ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}🐳 Docker não encontrado. Instalando Docker Engine...${NC}"
    sudo apt update
    sudo apt install -y docker.io docker-compose-v2
    sudo systemctl enable docker --now
    echo -e "${GREEN}✅ Docker instalado com sucesso.${NC}"
else
    echo -e "${GREEN}✅ Docker já está instalado no sistema.${NC}"
fi

# 4. Configurar Diretórios Persistentes no Host
echo -e "${YELLOW}📁 Configurando diretórios persistentes de uploads e logs...${NC}"
sudo mkdir -p /var/www/portalcursos/uploads
sudo chown -R $USER:$USER /var/www/portalcursos/uploads
mkdir -p "$PROJECT_ROOT/logs"

# 5. Configurar o Firewall Local (UFW)
echo -e "${YELLOW}🛡️  Configurando Firewall Operacional...${NC}"
sudo apt install ufw -y
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp comment 'SSH'
sudo ufw allow 80/tcp comment 'HTTP'
sudo ufw allow 443/tcp comment 'HTTPS'
# Garantir que a porta do Postgres (5432) e do Backend (8080) estejam fechadas externamente
sudo ufw deny 5432 comment 'Bloqueio Externo Postgres'
sudo ufw deny 8080 comment 'Bloqueio Externo Backend'
sudo ufw --force enable
echo -e "${GREEN}✅ Firewall robusto configurado e ativo.${NC}"

# 6. Build e Inicialização coordenada com Docker Compose
echo -e "${YELLOW}🏗️  Subindo Pilha de Containers (Postgres, Backend e Frontend)...${NC}"
cd "$DEVOPS_DIR"
# Remove imagens órfãs e recria a pilha
docker compose -f docker-compose.prod.yml down --remove-orphans || true
docker compose -f docker-compose.prod.yml up -d --build

echo -e "${GREEN}✅ Pilha de containers inicializada com sucesso.${NC}"
docker compose -f docker-compose.prod.yml ps

# 7. Configuração do Proxy Reverso Nginx no Host
echo -e "${YELLOW}🌐 Configurando Proxy Reverso Nginx...${NC}"
if [ -f "$SCRIPTS_DIR/nginx.conf" ]; then
    sudo cp "$SCRIPTS_DIR/nginx.conf" /etc/nginx/sites-available/portalcursos
    
    # Ajustar caminhos de uploads caso necessário no nginx.conf
    sudo sed -i 's|alias /var/www/portalcursos/backend/uploads;|alias /var/www/portalcursos/uploads;|g' /etc/nginx/sites-available/portalcursos

    # Ativar o site no Nginx
    sudo ln -sf /etc/nginx/sites-available/portalcursos /etc/nginx/sites-enabled/
    sudo rm -f /etc/nginx/sites-enabled/default || true
    
    # Validar as diretivas
    sudo nginx -t
    sudo systemctl reload nginx
    echo -e "${GREEN}✅ Proxy Reverso Nginx ativo e direcionado para os containers.${NC}"
else
    echo -e "${RED}⚠️  Aviso: nginx.conf não encontrado em $SCRIPTS_DIR/nginx.conf. Nginx não configurado.${NC}"
fi

# 8. Instalar SSL Criptografado Automatizado (Let's Encrypt / Certbot)
echo -e "${YELLOW}🔐 Configurando segurança SSL/HTTPS automatizada...${NC}"
sudo apt install -y certbot python3-certbot-nginx

# Obter o domínio definido no .env ou usar o padrão se ausente
DOMAIN_NAME=$(grep -v '^#' "$PROJECT_ROOT/.env" | grep 'DOMAIN_NAME' | cut -d= -f2 || echo "portalcursos.ng")
EMAIL_ADDRESS=$(grep -v '^#' "$PROJECT_ROOT/.env" | grep 'EMAIL_ADDRESS' | cut -d= -f2 || echo "ti@portalcursos.com")

if [[ "$DOMAIN_NAME" != "portalcursos.ng" ]]; then
    echo -e "${BLUE}📜 Solicitando certificado SSL para o domínio: $DOMAIN_NAME...${NC}"
    # Substituir domínio temporário pelo domínio real no arquivo do Nginx
    sudo sed -i "s/portalcursos.ng/$DOMAIN_NAME/g" /etc/nginx/sites-available/portalcursos
    sudo sed -i "s/www.portalcursos.ng/www.$DOMAIN_NAME/g" /etc/nginx/sites-available/portalcursos
    sudo systemctl reload nginx
    
    # Obter certificado com redirecionamento automático
    sudo certbot --nginx -d "$DOMAIN_NAME" -d "www.$DOMAIN_NAME" --agree-tos -m "$EMAIL_ADDRESS" --non-interactive --redirect || {
        echo -e "${RED}⚠️  Não foi possível obter o SSL. O Nginx continuará servindo em HTTP.${NC}"
        echo -e "${RED}Verifique se os registros de DNS A estão apontados para o IP desta VPS.${NC}"
    }
else
    echo -e "${YELLOW}⚠️  Domínio padrão detectado. Pulando emissão do SSL.${NC}"
    echo -e "${YELLOW}Configure o DOMAIN_NAME no .env com seu domínio real para emissão do SSL.${NC}"
fi

echo -e "${GREEN}====================================================${NC}"
echo -e "${GREEN}🎉 DEPLOY OMEGA DOCKER CONCLUÍDO COM SUCESSO!${NC}"
echo -e "${GREEN}Para monitorar logs do sistema rodando:${NC}"
echo -e "${BLUE}  docker compose -f devops/docker-compose.prod.yml logs -f${NC}"
echo -e "${GREEN}====================================================${NC}"
