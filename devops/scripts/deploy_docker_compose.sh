#!/bin/bash

# =================================================================
# OMEGA-SUPREME: Script de Automação Definitiva de Deploy Docker
# PortalCursos.NG - Versão 2026.05 (Docker/Nginx/SSL Edition)
# =================================================================

set -e

# Garantir que sbin está no PATH para comandos do sistema como UFW
export PATH=$PATH:/usr/sbin:/sbin:/usr/local/sbin

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

# 2. Carregar variáveis de ambiente do arquivo .env de forma resiliente a CRLF
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo -e "${BLUE}📄 Carregando variáveis de ambiente do .env...${NC}"
    # Remove carriage returns (\r) do Windows e exporta cada variável de forma segura
    while IFS= read -r line || [ -n "$line" ]; do
        # Ignora comentários e linhas vazias
        if [[ ! "$line" =~ ^# ]] && [[ ! -z "$line" ]]; then
            clean_line=$(echo "$line" | tr -d '\r')
            if [[ "$clean_line" == *"="* ]]; then
                export "$clean_line"
            fi
        fi
    done < "$PROJECT_ROOT/.env"
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

# 5. Configurar o Firewall Local (UFW) de Forma Resiliente
echo -e "${YELLOW}🛡️  Configurando Firewall Operacional...${NC}"
UFW_BIN=$(command -v ufw || echo "/usr/sbin/ufw")

if ! command -v ufw &> /dev/null && [ ! -x "/usr/sbin/ufw" ] && [ ! -x "/sbin/ufw" ]; then
    echo -e "${YELLOW}⚠️  UFW não encontrado. Tentando instalar...${NC}"
    # Atualiza lista de pacotes e tenta instalar o ufw, mas sem quebrar se falhar
    sudo apt-get update -y && sudo apt-get install ufw -y || true
fi

# Localiza novamente o executável do ufw
UFW_BIN=$(command -v ufw || echo "/usr/sbin/ufw")
if [ -x "$UFW_BIN" ] || command -v ufw &> /dev/null; then
    echo -e "${BLUE}🛡️  Aplicando regras do firewall via $UFW_BIN...${NC}"
    sudo "$UFW_BIN" default deny incoming || true
    sudo "$UFW_BIN" default allow outgoing || true
    sudo "$UFW_BIN" allow 22/tcp comment 'SSH' || true
    sudo "$UFW_BIN" allow 80/tcp comment 'HTTP' || true
    sudo "$UFW_BIN" allow 443/tcp comment 'HTTPS' || true
    sudo "$UFW_BIN" allow 3010/tcp comment 'Permitir Frontend NextJS' || true
    sudo "$UFW_BIN" allow 8090/tcp comment 'Permitir Backend SpringBoot' || true
    sudo "$UFW_BIN" deny 5432 comment 'Bloqueio Externo Postgres' || true
    sudo "$UFW_BIN" --force enable || true
    echo -e "${GREEN}✅ Firewall robusto configurado e ativo com portas PortalCursos abertas.${NC}"
else
    echo -e "${YELLOW}⚠️  Aviso: Não foi possível configurar o UFW (binário não localizado). Ignorando firewall para evitar travar o deploy.${NC}"
fi

# 6. Build e Inicialização coordenada com Docker Compose
echo -e "${YELLOW}🏗️  Subindo Pilha de Containers (Postgres, Backend e Frontend)...${NC}"
cd "$DEVOPS_DIR"
# Remove imagens órfãs e recria a pilha
docker compose -f docker-compose.prod.yml down --remove-orphans || true
docker compose -f docker-compose.prod.yml build --no-cache
docker compose -f docker-compose.prod.yml up -d

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
    
    # Validar as diretivas de forma protegida contra falhas (resiliência contra colisões de porta/EasyPanel)
    if sudo nginx -t &>/dev/null; then
        if sudo systemctl reload nginx &>/dev/null; then
            echo -e "${GREEN}✅ Proxy Reverso Nginx ativo e direcionado para os containers.${NC}"
        else
            echo -e "${YELLOW}⚠️  Aviso: Falha ao recarregar o Nginx. Verifique se o EasyPanel ou outro serviço ocupa as portas 80/443.${NC}"
        fi
    else
        echo -e "${RED}⚠️  Aviso: Configuração do Nginx possui erros de sintaxe ou as portas estão em conflito. A aplicação continuará acessível diretamente na porta 3010.${NC}"
    fi
else
    echo -e "${RED}⚠️  Aviso: nginx.conf não encontrado em $SCRIPTS_DIR/nginx.conf. Nginx não configurado.${NC}"
fi

# 8. Instalar SSL Criptografado Automatizado (Let's Encrypt / Certbot)
echo -e "${YELLOW}🔐 Configurando segurança SSL/HTTPS automatizada...${NC}"
sudo apt install -y certbot python3-certbot-nginx

# Obter o domínio definido no .env ou usar o padrão se ausente
DOMAIN_NAME=$(grep -v '^#' "$PROJECT_ROOT/.env" | grep 'DOMAIN_NAME' | cut -d= -f2 || echo "portalcursos.ng")
EMAIL_ADDRESS=$(grep -v '^#' "$PROJECT_ROOT/.env" | grep 'EMAIL_ADDRESS' | cut -d= -f2 || echo "ti@portalcursos.com")

if [[ -n "$DOMAIN_NAME" && "$DOMAIN_NAME" != "localhost" ]]; then
    echo -e "${BLUE}📜 Solicitando certificado SSL para o dominio: $DOMAIN_NAME...${NC}"
    # Garantir que o certificado autoassinado inicial exista para permitir que o Nginx inicie antes do Certbot rodar
    if [ ! -f "/etc/letsencrypt/live/$DOMAIN_NAME/fullchain.pem" ]; then
        echo -e "${YELLOW}⚠️ Certificado nao encontrado. Criando placeholder temporario para inicializar o Nginx...${NC}"
        sudo mkdir -p "/etc/letsencrypt/live/$DOMAIN_NAME/"
        sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
          -keyout "/etc/letsencrypt/live/$DOMAIN_NAME/privkey.pem" \
          -out "/etc/letsencrypt/live/$DOMAIN_NAME/fullchain.pem" \
          -subj "/CN=$DOMAIN_NAME"
    fi
    
    # Substituir dominio temporario pelo dominio real no arquivo do Nginx
    sudo sed -i "s/portalcursos.ng/$DOMAIN_NAME/g" /etc/nginx/sites-available/portalcursos
    sudo sed -i "s/www.portalcursos.ng/www.$DOMAIN_NAME/g" /etc/nginx/sites-available/portalcursos
    sudo systemctl restart nginx || true
    
    # Obter certificado com redirecionamento automatico
    sudo certbot --nginx -d "$DOMAIN_NAME" -d "www.$DOMAIN_NAME" --agree-tos -m "$EMAIL_ADDRESS" --non-interactive --redirect || {
        echo -e "${RED}⚠️  Nao foi possivel obter o SSL Let's Encrypt. A aplicacao podera ser servida com o certificado de contingencia.${NC}"
        echo -e "${RED}Verifique se os registros de DNS A estao apontados para o IP desta VPS.${NC}"
    }
else
    echo -e "${YELLOW}⚠️  Dominio local ou ausente detectado. Pulando emissao do SSL Let's Encrypt.${NC}"
fi

echo -e "${GREEN}====================================================${NC}"
echo -e "${GREEN}🎉 DEPLOY OMEGA DOCKER CONCLUÍDO COM SUCESSO!${NC}"
echo -e "${GREEN}Para monitorar logs do sistema rodando:${NC}"
echo -e "${BLUE}  docker compose -f devops/docker-compose.prod.yml logs -f${NC}"
echo -e "${GREEN}====================================================${NC}"
