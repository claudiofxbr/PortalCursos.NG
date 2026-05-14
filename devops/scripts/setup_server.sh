#!/bin/bash

# =================================================================
# OMEGA-SUPREME: Server Setup Script
# Versão: 1.0
# Destino: Hostinger VPS (Ubuntu/Debian)
# =================================================================

set -e

echo "🚀 Iniciando configuração do servidor PortalCursos.NG..."

# Atualizar Sistema
sudo apt update && sudo apt upgrade -y

# Instalar Java 17 (Backend)
echo "☕ Instalando OpenJDK 17..."
sudo apt install openjdk-17-jdk -y

# Instalar Node.js 20 (Frontend)
echo "🟢 Instalando Node.js 20..."
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Instalar Ferramentas Essenciais
echo "🛠️ Instalando ferramentas de build e gestão..."
sudo apt install maven git nginx -y
sudo npm install -g pm2

# Configurar Firewall (UFW)
echo "🛡️ Configurando Firewall..."
sudo ufw allow 22
sudo ufw allow 'Nginx Full'
# Porta 8080 deve ser acessada apenas via Nginx Proxy em Produção
sudo ufw deny 8080
sudo ufw --force enable

echo "✅ Configuração básica concluída!"
echo "⚠️  Lembre-se de configurar o Nginx usando o template nginx.conf fornecido."
