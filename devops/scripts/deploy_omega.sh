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

# --- FASE: SANITY CHECK & ESTRUTURA ---
echo -e "${BLUE}🔍 Verificando integridade dos diretórios...${NC}"

# Criar pasta de logs se não existir
if [ ! -d "$PROJECT_ROOT/logs" ]; then
    echo "📁 Criando diretório de logs..."
    mkdir -p "$PROJECT_ROOT/logs"
fi

# Criar pasta de uploads para documentos (CRÍTICO para o backend)
if [ ! -d "$BACKEND_DIR/uploads" ]; then
    echo "📁 Criando diretório de uploads no backend..."
    mkdir -p "$BACKEND_DIR/uploads"
fi

# Garantir que o .env exista (obrigatório)
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${RED}❌ ERRO CRÍTICO: Arquivo .env não encontrado na raiz!${NC}"
    echo -e "${RED}Crie o arquivo .env com as credenciais antes de fazer deploy.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Estrutura de diretórios validada.${NC}"

# --- FIM DA FASE DE SANITY CHECK ---

# Carregar variáveis de ambiente se o .env existir
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${BLUE}📄 Carregando variáveis de ambiente do .env...${NC}"
    while IFS= read -r line || [ -n "$line" ]; do
        line=$(echo "$line" | xargs)
        if [[ -z "$line" || "$line" =~ ^# ]]; then
            continue
        fi
        if [[ "$line" =~ ^([^=]+)=(.*)$ ]]; then
            key="${BASH_REMATCH[1]}"
            value="${BASH_REMATCH[2]}"
            value="${value#\"}"
            value="${value%\"}"
            value="${value#\'}"
            value="${value%\'}"
            export "$key=$value"
        fi
    done < "$PROJECT_ROOT/.env"
fi

# Verificar variáveis críticas
for VAR in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD APP_JWT_SECRET; do
    if [ -z "${!VAR}" ]; then
        echo -e "${RED}❌ ERRO CRÍTICO: Variável $VAR não definida no .env. Deploy abortado.${NC}"
        exit 1
    fi
done
echo -e "${GREEN}✅ Variáveis de ambiente validadas.${NC}"

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
