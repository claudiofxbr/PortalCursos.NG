#!/bin/bash

# =================================================================
# SCRIPT DE DEPLOY AUTOMATIZADO - PortalCursos.NG
# Destino: Hostinger VPS (Ubuntu/Debian)
# Versão: 2.0 (Refatorado para Stack Java/Next.js)
# =================================================================

set -e

# Cores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}🚀 Iniciando Deploy do PortalCursos.NG na Hostinger...${NC}"

# 1. Verificação e instalação de dependências
echo -e "${YELLOW}Step 1: Instalando dependências (JDK 17, Node 20, Maven, Nginx, PM2)...${NC}"
sudo apt update && sudo apt upgrade -y

# Java 17 para o Backend
sudo apt install openjdk-17-jdk -y

# Node.js 20 para o Frontend
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Ferramentas de Build e Servidor
sudo apt install maven git nginx ufw -y
sudo npm install -g pm2

# 2. Preparação do ambiente
echo -e "${YELLOW}Step 2: Configurando diretórios e permissões...${NC}"
APP_ROOT="/var/www/portalcursos"
sudo mkdir -p $APP_ROOT
sudo chown -R $USER:$USER $APP_ROOT

# Configuração de Firewall
echo -e "${BLUE}🛡️ Configurando Firewall...${NC}"
sudo ufw allow 22
sudo ufw allow 'Nginx Full'
sudo ufw --force enable

# 3. Configuração de Banco de Dados
echo -e "${YELLOW}Step 3: Instruções de Banco de Dados...${NC}"
echo -e "${GREEN}Este sistema utiliza Neon (PostgreSQL). Certifique-se de que o dump inicial foi importado via console do Neon.${NC}"

# 4. Variáveis de ambiente
echo -e "${YELLOW}Step 4: Criando arquivo .env com placeholders...${NC}"
cat <<EOF > $APP_ROOT/.env
# --- BACKEND (Spring Boot) ---
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-example-123456.us-east-2.aws.neon.tech/portal_db?sslmode=require
SPRING_DATASOURCE_USERNAME=seu_usuario_neon
SPRING_DATASOURCE_PASSWORD=sua_senha_neon
JWT_SECRET=gere_uma_chave_segura_aqui

# --- FRONTEND (Next.js) ---
NEXT_PUBLIC_API_URL=http://localhost:8080/api
PORT=3000
EOF

echo -e "${GREEN}✅ Arquivo .env criado em $APP_ROOT/.env. Por favor, edite-o com suas credenciais reais.${NC}"

# 5. Pós-instalação e Otimização
echo -e "${YELLOW}Step 5: Otimização e Segurança...${NC}"

# Script de build simplificado (exemplo)
# cd $APP_ROOT/backend && mvn clean package -DskipTests
# cd $APP_ROOT/frontend && npm install && npm run build

# Limpeza de logs antigos
pm2 flush

# Ajuste final de permissões
sudo find $APP_ROOT -type d -exec chmod 755 {} +
sudo find $APP_ROOT -type f -exec chmod 644 {} +

echo -e "${GREEN}====================================================${NC}"
echo -e "${GREEN}🎉 Deploy Base Concluído com Sucesso!${NC}"
echo -e "${YELLOW}Próximos passos:${NC}"
echo -e "1. Faça o git clone do seu código dentro de $APP_ROOT"
echo -e "2. Configure o Nginx em /etc/nginx/sites-available/"
echo -e "3. Inicie as aplicações via PM2: pm2 start ecosystem.config.js"
echo -e "${GREEN}====================================================${NC}"
