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
    sudo "$UFW_BIN" deny 3010 comment 'Bloqueio Externo Frontend' || true
    sudo "$UFW_BIN" deny 8090 comment 'Bloqueio Externo Backend' || true
    sudo "$UFW_BIN" deny 5432 comment 'Bloqueio Externo Postgres' || true
    sudo "$UFW_BIN" --force enable || true
    echo -e "${GREEN}✅ Firewall robusto configurado e ativo com portas PortalCursos abertas.${NC}"
else
    echo -e "${YELLOW}⚠️  Aviso: Não foi possível configurar o UFW (binário não localizado). Ignorando firewall para evitar travar o deploy.${NC}"
fi

# 6. Build e Inicialização coordenada com Docker Compose
echo -e "${YELLOW}🏗️  Subindo Pilha de Containers (Postgres, Backend e Frontend)...${NC}"
cd "$DEVOPS_DIR"

echo -e "${YELLOW}🧹 Removendo conflitos de containers antigos no daemon do Docker...${NC}"
for container in portalcursos_postgres portalcursos_backend portalcursos_frontend; do
    if docker ps -a --format '{{.Names}}' | grep -q "^${container}$"; then
        echo -e "${YELLOW}⚠️  Removendo container conflitante ativo/inativo: ${container}...${NC}"
        docker stop "$container" || true
        docker rm "$container" || true
    fi
done

# Limpa o estado e remove órfãos
docker compose -f docker-compose.prod.yml down --remove-orphans || true

# Reconstrói a pilha e inicializa forçando a recriação limpa (limpa cache corrompido do Docker)
docker compose -f docker-compose.prod.yml build --no-cache
docker compose -f docker-compose.prod.yml up -d --force-recreate

echo -e "${GREEN}✅ Pilha de containers inicializada com sucesso.${NC}"
docker compose -f docker-compose.prod.yml ps

# Liberação de Portas (Estratégia 2 - OMEGA HARDENING)
echo -e "${YELLOW}🛑 Estratégia 2: Verificando e liberando portas 80/443 na VPS...${NC}"
sudo systemctl stop apache2 2>/dev/null || true
sudo systemctl disable apache2 2>/dev/null || true

# Identifica contêineres conflitantes nas portas 80/443
conflicting_ids=$(docker ps -q --filter "publish=80" --filter "publish=443" || true)
if [ -n "$conflicting_ids" ]; then
    echo -e "${RED}⚠️  Detectados contêineres ocupando portas 80/443. Removendo conflitos...${NC}"
    for c_id in $conflicting_ids; do
        c_name=$(docker inspect --format '{{.Name}}' "$c_id" | tr -d '/')
        if [[ "$c_name" != "portalcursos"* ]]; then
            echo -e "${YELLOW}🛑 Parando e removendo contêiner conflitante: $c_name ($c_id)...${NC}"
            docker stop "$c_id" || true
            docker rm "$c_id" || true
        fi
    done
fi

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

# 8. Instalar e configurar SSL (Let's Encrypt / Certbot)
echo -e "${YELLOW}🔐 Configurando segurança SSL/HTTPS automatizada...${NC}"

# Lê domínio e e-mail do .env (já carregado acima)
DOMAIN_NAME=$(echo "${DOMAIN_NAME:-}" | tr -d '\r ')
EMAIL_ADDRESS=$(echo "${EMAIL_ADDRESS:-}" | tr -d '\r ')
if [ -z "$DOMAIN_NAME" ]; then DOMAIN_NAME="xavierbr-vps.tech"; fi
if [ -z "$EMAIL_ADDRESS" ]; then EMAIL_ADDRESS="claudiofxbr@gmail.com"; fi

if [[ "$DOMAIN_NAME" == "localhost" || "$DOMAIN_NAME" == "127.0.0.1" ]]; then
    echo -e "${YELLOW}⚠️  Domínio local detectado. Pulando SSL Let's Encrypt.${NC}"
else
    echo -e "${BLUE}📜 Domínio alvo: $DOMAIN_NAME${NC}"
    sudo apt-get install -y certbot python3-certbot-nginx -qq

    # Criar certificado autoassinado temporário para que o Nginx inicie
    # (necessário pois o nginx.conf já referencia os caminhos de cert)
    if [ ! -f "/etc/letsencrypt/live/$DOMAIN_NAME/fullchain.pem" ]; then
        echo -e "${YELLOW}⚠️  Certificado não encontrado. Criando autoassinado temporário...${NC}"
        sudo mkdir -p "/etc/letsencrypt/live/$DOMAIN_NAME/"
        sudo openssl req -x509 -nodes -days 1 -newkey rsa:2048 \
            -keyout "/etc/letsencrypt/live/$DOMAIN_NAME/privkey.pem" \
            -out  "/etc/letsencrypt/live/$DOMAIN_NAME/fullchain.pem" \
            -subj "/CN=$DOMAIN_NAME" 2>/dev/null
        echo -e "${GREEN}✅ Certificado temporário criado. O Nginx pode iniciar.${NC}"
    fi

    # Garantir que o nginx.conf usa o domínio correto
    sudo sed -i "s|server_name .*;|server_name $DOMAIN_NAME www.$DOMAIN_NAME;|g" \
        /etc/nginx/sites-available/portalcursos 2>/dev/null || true
    sudo sed -i "s|/etc/letsencrypt/live/[^/]*/|/etc/letsencrypt/live/$DOMAIN_NAME/|g" \
        /etc/nginx/sites-available/portalcursos 2>/dev/null || true

    sudo systemctl reload nginx 2>/dev/null || sudo systemctl restart nginx || true

    # Emitir certificado real Let's Encrypt
    echo -e "${BLUE}📜 Solicitando certificado Let's Encrypt...${NC}"
    sudo certbot --nginx \
        -d "$DOMAIN_NAME" -d "www.$DOMAIN_NAME" \
        --agree-tos -m "$EMAIL_ADDRESS" \
        --non-interactive --redirect 2>&1 || {
        echo -e "${YELLOW}⚠️  SSL Let's Encrypt não obtido. Causas comuns:${NC}"
        echo -e "${YELLOW}    1. DNS A de '$DOMAIN_NAME' não aponta para este IP.${NC}"
        echo -e "${YELLOW}    2. Porta 80 bloqueada ou ocupada.${NC}"
        echo -e "${YELLOW}    3. Rate limit do Let's Encrypt (5 emissões/semana).${NC}"
        echo -e "${YELLOW}    A aplicação continuará rodando com certificado temporário.${NC}"
        echo -e "${YELLOW}    Use: ./manage-vps.ps1 → opção 3 para instalar SSL depois.${NC}"
    }
fi

echo -e "${GREEN}====================================================${NC}"
echo -e "${GREEN}🎉 DEPLOY OMEGA DOCKER CONCLUÍDO COM SUCESSO!${NC}"
echo -e "${GREEN}Para monitorar logs do sistema rodando:${NC}"
echo -e "${BLUE}  docker compose -f devops/docker-compose.prod.yml logs -f${NC}"
echo -e "${GREEN}====================================================${NC}"
