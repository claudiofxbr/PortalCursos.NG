#!/bin/bash

# =================================================================
# OMEGA-SUPREME: Script Unificado de Deploy Final
# PortalCursos.NG - Versão 2026.05
# =================================================================

set -e

# Cores para output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}🚀 Iniciando Deploy Unificado (Backend + Frontend)...${NC}"

# 1. Caminhos
PROJECT_ROOT="$(pwd)"
if [[ "$PROJECT_ROOT" == *"devops/scripts"* ]]; then
    PROJECT_ROOT="$(dirname "$(dirname "$PROJECT_ROOT")")"
fi

BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
ECOSYSTEM_CONFIG="$PROJECT_ROOT/devops/scripts/ecosystem.config.js"

# Carregar variáveis de ambiente se o .env existir
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${BLUE}📄 Carregando variáveis de ambiente do .env...${NC}"
    export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
fi

# 2. Build do Backend (Java/Spring Boot)
echo -e "${YELLOW}🏗️  Construindo Backend...${NC}"
cd "$BACKEND_DIR"
./mvnw clean package -DskipTests

# 3. Build do Frontend (Next.js)
echo -e "${YELLOW}🏗️  Construindo Frontend...${NC}"
cd "$FRONTEND_DIR"
if [ ! -d "node_modules" ]; then
    echo "📦 Instalando dependências do frontend..."
    npm install
fi
npm run build

# 4. Orquestração via PM2
echo -e "${YELLOW}🔄 Reiniciando serviços via PM2...${NC}"
cd "$PROJECT_ROOT"
pm2 restart "$ECOSYSTEM_CONFIG" || pm2 start "$ECOSYSTEM_CONFIG"

# 5. Finalização
echo -e "${GREEN}====================================================${NC}"
echo -e "${GREEN}🎉 Deploy Final Concluído com Sucesso!${NC}"
echo -e "${GREEN}Serviços em operação:${NC}"
pm2 status
echo -e "${GREEN}====================================================${NC}"
