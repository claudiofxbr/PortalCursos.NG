#!/bin/bash

# =================================================================
# OMEGA-SUPREME: Frontend Deployment Script
# =================================================================

set -e

PROJECT_ROOT="/home/$(whoami)/PortalCursos.NG"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

echo "🌐 Iniciando Deploy do Frontend..."

cd $FRONTEND_DIR

# 1. Instalar dependências
echo "📥 Instalando dependências..."
npm install

# 2. Build Next.js
echo "🏗️  Gerando build de produção..."
npm run build

# 3. Reiniciar com PM2
echo "🔄 Reiniciando serviço Next.js..."
pm2 restart ecosystem.config.js --only portalcursos-frontend || pm2 start ecosystem.config.js --only portalcursos-frontend

echo "✅ Frontend em operação!"
pm2 status portalcursos-frontend
