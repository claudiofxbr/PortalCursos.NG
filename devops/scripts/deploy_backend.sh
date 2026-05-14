#!/bin/bash

# =================================================================
# OMEGA-SUPREME: Backend Deployment Script
# =================================================================

set -e

PROJECT_ROOT="/home/$(whoami)/PortalCursos.NG"
BACKEND_DIR="$PROJECT_ROOT/backend"

echo "📦 Iniciando Deploy do Backend..."

cd $BACKEND_DIR

# 1. Pull changes (Opcional se rodar localmente antes do upload)
# git pull origin main

# 2. Build com Maven
echo "🏗️  Compilando projeto com Maven..."
./mvnw clean package -DskipTests

# 3. Limpeza e Reinicialização
echo "🧹 Liberando porta 8080 caso esteja em uso..."
sudo fuser -k 8080/tcp || true

echo "🔄 Reiniciando serviço Spring Boot..."
pm2 restart ecosystem.config.js --only portalcursos-backend || pm2 start ecosystem.config.js --only portalcursos-backend

echo "✅ Backend em operação!"
pm2 status portalcursos-backend
