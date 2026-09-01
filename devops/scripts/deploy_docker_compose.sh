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

# 6. Build e Inicialização coordenada com Docker Compose (com health check e rollback)
echo -e "${YELLOW}🏗️  Subindo Pilha de Containers (Postgres, Backend e Frontend)...${NC}"
cd "$DEVOPS_DIR"

# docker-compose.prod.yml declara "easypanel" como rede externa obrigatória —
# precisa existir antes do primeiro "docker compose up" ou o comando falha.
docker network create easypanel 2>/dev/null && echo -e "${GREEN}✅ Rede easypanel criada.${NC}" || echo -e "${GREEN}✅ Rede easypanel já existe.${NC}"

# Guarda a imagem anterior de backend/frontend para permitir rollback automático
# se o health check abaixo falhar (mesmo padrão de robustez usado em deploy_ci.sh).
PREV_BACKEND_IMAGE_ID=$(docker inspect -f '{{.Image}}' portalcursos_backend 2>/dev/null || echo "")
PREV_BACKEND_IMAGE_TAG=$(docker inspect -f '{{.Config.Image}}' portalcursos_backend 2>/dev/null || echo "")
PREV_FRONTEND_IMAGE_ID=$(docker inspect -f '{{.Image}}' portalcursos_frontend 2>/dev/null || echo "")
PREV_FRONTEND_IMAGE_TAG=$(docker inspect -f '{{.Config.Image}}' portalcursos_frontend 2>/dev/null || echo "")

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

# Reconstrói a pilha (limpa cache corrompido do Docker)
docker compose -f docker-compose.prod.yml build --no-cache

echo -e "${YELLOW}🐘 Subindo PostgreSQL...${NC}"
docker compose -f docker-compose.prod.yml up -d postgres

echo -e "${YELLOW}🚀 Subindo backend e frontend...${NC}"
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps backend
docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps frontend

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

# 7. Health check com timeout e rollback automático em caso de falha
echo -e "${YELLOW}⏳ Aguardando containers ficarem saudáveis...${NC}"
BACKEND_READY=false
for i in $(seq 1 45); do
    if curl -sf http://127.0.0.1:8090/api/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Backend saudável (${i}x2s).${NC}"
        BACKEND_READY=true
        break
    fi
    sleep 2
done
if [ "$BACKEND_READY" = "false" ]; then
    echo -e "${RED}⚠️  Backend não respondeu em 90s — logs:${NC}"
    docker logs portalcursos_backend --tail 30 2>&1 | sed 's/^/  /'
fi

FRONTEND_READY=false
for i in $(seq 1 60); do
    CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010/portalcursos.ng 2>/dev/null || echo "000")
    if [ "$CODE" = "200" ] || [ "$CODE" = "308" ] || [ "$CODE" = "302" ]; then
        echo -e "${GREEN}✅ Frontend respondendo HTTP $CODE (${i}x2s).${NC}"
        FRONTEND_READY=true
        break
    fi
    sleep 2
done
if [ "$FRONTEND_READY" = "false" ]; then
    echo -e "${RED}⚠️  Frontend não respondeu em 120s — logs:${NC}"
    docker logs portalcursos_frontend --tail 30 2>&1 | sed 's/^/  /'
fi

if [ "$BACKEND_READY" = "false" ] || [ "$FRONTEND_READY" = "false" ]; then
    echo -e "${RED}⚠️  Health check falhou — Backend OK: $BACKEND_READY, Frontend OK: $FRONTEND_READY. Tentando rollback para a imagem anterior...${NC}"

    ROLLBACK_OK=true
    if [ -n "$PREV_BACKEND_IMAGE_ID" ] && [ -n "$PREV_BACKEND_IMAGE_TAG" ]; then
        docker tag "$PREV_BACKEND_IMAGE_ID" "$PREV_BACKEND_IMAGE_TAG"
        docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps backend
        RB_BACKEND_READY=false
        for i in $(seq 1 45); do
            if curl -sf http://127.0.0.1:8090/api/health > /dev/null 2>&1; then RB_BACKEND_READY=true; break; fi
            sleep 2
        done
        [ "$RB_BACKEND_READY" = "true" ] && echo -e "${GREEN}✅ Rollback do backend OK.${NC}" || { echo -e "${RED}❌ Rollback do backend também falhou.${NC}"; ROLLBACK_OK=false; }
    else
        echo -e "${YELLOW}⚠️  Sem imagem anterior de backend conhecida — rollback automático não é possível.${NC}"
        ROLLBACK_OK=false
    fi

    if [ -n "$PREV_FRONTEND_IMAGE_ID" ] && [ -n "$PREV_FRONTEND_IMAGE_TAG" ]; then
        docker tag "$PREV_FRONTEND_IMAGE_ID" "$PREV_FRONTEND_IMAGE_TAG"
        docker compose -f docker-compose.prod.yml up -d --force-recreate --no-deps frontend
        RB_FRONTEND_READY=false
        for i in $(seq 1 60); do
            CODE=$(curl -sf -o /dev/null -w "%{http_code}" http://127.0.0.1:3010/portalcursos.ng 2>/dev/null || echo "000")
            if [ "$CODE" = "200" ] || [ "$CODE" = "308" ] || [ "$CODE" = "302" ]; then RB_FRONTEND_READY=true; break; fi
            sleep 2
        done
        [ "$RB_FRONTEND_READY" = "true" ] && echo -e "${GREEN}✅ Rollback do frontend OK.${NC}" || { echo -e "${RED}❌ Rollback do frontend também falhou.${NC}"; ROLLBACK_OK=false; }
    else
        echo -e "${YELLOW}⚠️  Sem imagem anterior de frontend conhecida — rollback automático não é possível.${NC}"
        ROLLBACK_OK=false
    fi

    if [ "$ROLLBACK_OK" = "true" ]; then
        echo -e "${RED}❌ Deploy da nova versão falhou no health check, mas o rollback para a versão anterior teve sucesso — produção permanece no ar com o código antigo. Corrija o problema e tente novamente.${NC}"
        exit 1
    else
        echo -e "${RED}❌ Deploy da nova versão falhou no health check E o rollback automático também falhou — produção pode estar fora do ar. Intervenção manual urgente necessária.${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✅ Pilha de containers saudável e inicializada com sucesso.${NC}"

# 8. Conectar containers à rede Traefik (EasyPanel) e recarregar
# A arquitetura de produção usa Traefik (gerenciado pelo EasyPanel) como único
# proxy de borda — a configuração manual de Nginx/Certbot que existia aqui foi
# removida porque conflita com as portas 80/443 do Traefik. Evidência: (1)
# devops/scripts/fix_502.sh para e desabilita o Nginx explicitamente sempre que
# o encontra ativo, com o comentário "Nginx NAO e usado — Traefik e o unico
# proxy de borda"; (2) devops/scripts/setup_server.sh documenta que o setup da
# VPS "NAO instala Java, Node, Nginx ou pm2 — tudo roda em containers Docker" e
# que "o Traefik e gerenciado pelo EasyPanel"; (3) devops/docker-compose.prod.yml
# já define todo o roteamento e TLS via labels Traefik (certresolver=letsencrypt),
# sem depender de nginx.conf; (4) deploy_ci.sh e deploy_vps.sh (os outros dois
# scripts de deploy do projeto) seguem esse mesmo padrão só-Traefik, sem Nginx.
echo -e "${YELLOW}🌐 Conectando containers à rede Traefik (easypanel)...${NC}"
for container in portalcursos_backend portalcursos_frontend; do
    already=$(docker inspect "$container" \
        --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' 2>/dev/null \
        | tr ' ' '\n' | grep -c "^easypanel$" || true)
    if [ "${already:-0}" -eq 0 ]; then
        docker network connect easypanel "$container" && echo -e "${GREEN}✅ $container -> rede easypanel${NC}"
    else
        echo -e "${GREEN}✅ $container já está na rede easypanel${NC}"
    fi
done

TRAEFIK_CONTAINER=$(docker ps --filter name=easypanel-traefik -q 2>/dev/null | head -1 || true)
if [ -n "$TRAEFIK_CONTAINER" ]; then
    docker kill --signal=SIGHUP "$TRAEFIK_CONTAINER"
    echo -e "${GREEN}✅ Traefik recarregado (SIGHUP).${NC}"
else
    echo -e "${YELLOW}⚠️  Container Traefik não encontrado — reload ignorado.${NC}"
fi

echo -e "${GREEN}====================================================${NC}"
echo -e "${GREEN}🎉 DEPLOY OMEGA DOCKER CONCLUÍDO COM SUCESSO!${NC}"
echo -e "${GREEN}Para monitorar logs do sistema rodando:${NC}"
echo -e "${BLUE}  docker compose -f devops/docker-compose.prod.yml logs -f${NC}"
echo -e "${GREEN}====================================================${NC}"
