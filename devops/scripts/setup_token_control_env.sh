#!/bin/bash
# Configura o .env do Controle de Tokens na VPS e valida a conexão com o Neon.
# Rode VOCÊ na VPS (como root). Os segredos são digitados aqui, com a tela oculta —
# nunca passam por chat, histórico do shell nem por argumentos de linha de comando.
#
# Uso:  bash setup_token_control_env.sh
# Cole a "connection string" do console do Neon (Connect > Connection string), no formato:
#   postgresql://USUARIO:SENHA@ep-xxxx-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require
# Você precisa de duas: a POOLED (host com "-pooler") e a DIRETA (sem "-pooler").
set -euo pipefail
umask 077

APP_DIR="${APP_DIR:-/var/www/token-control}"
ENV_FILE="$APP_DIR/.env"
BASE_PATH="${NEXT_PUBLIC_BASE_PATH:-/tokencontrol}"

die() { echo "ERRO: $*" >&2; exit 1; }
ask_secret() { # $1 = prompt ; resultado em $REPLY
    read -rsp "$1" REPLY < "${TTY:-/dev/tty}" || read -rsp "$1" REPLY; echo >&2
}
urldecode() { local s="${1//+/ }"; printf '%b' "${s//%/\\x}"; }

# postgresql://user:pass@host[:port]/db?params  ->  JDBC_URL, DB_USER, DB_PASS
parse_conn() {
    local c="$1"
    [[ "$c" =~ ^postgres(ql)?://([^:/@]+):([^@]+)@([^/?:]+)(:[0-9]+)?/([^?]+)(\?(.*))?$ ]] \
        || die "formato inválido. Esperado: postgresql://usuario:senha@host/banco?sslmode=require"
    DB_USER="$(urldecode "${BASH_REMATCH[2]}")"
    DB_PASS="$(urldecode "${BASH_REMATCH[3]}")"
    DB_HOST="${BASH_REMATCH[4]}"
    local port="${BASH_REMATCH[5]}" db="${BASH_REMATCH[6]}" q="${BASH_REMATCH[8]:-sslmode=require}"
    # channel_binding é parâmetro do libpq/psql; o driver JDBC não o usa — fora da URL JDBC
    q="$(sed -E 's/(^|&)channel_binding=[^&]*//g; s/^&//' <<< "$q")"; q="${q:-sslmode=require}"
    JDBC_URL="jdbc:postgresql://${DB_HOST}${port}/${db}?${q}"
    CONN_DB="$db"
}

test_conn() { # usa psql se existir; senha via variável de ambiente (não aparece em ps)
    if ! command -v psql >/dev/null 2>&1; then echo "AVISO: psql não instalado — conexão não testada (apt install postgresql-client)"; return 0; fi
    local url="postgresql://${DB_USER}@${DB_HOST}${1}/${CONN_DB}?${2}"
    PGPASSWORD="$DB_PASS" PGCONNECT_TIMEOUT=15 psql "$url" -Atqc "select 'ok'" 2>/dev/null | grep -qx ok
}

echo "== 1/4  Conexão POOLED (host com -pooler) — usada pela aplicação =="
ask_secret "Cole a connection string POOLED (oculta): "; POOLED="$REPLY"
parse_conn "$POOLED"; POOLED_URL="$JDBC_URL"; APP_USER="$DB_USER"; APP_PASS="$DB_PASS"; POOLED_HOST="$DB_HOST"
[[ "$POOLED_HOST" == *-pooler* ]] || echo "AVISO: o host não tem '-pooler'. Para o tráfego da aplicação o Neon recomenda a URL pooled."
[[ "$POOLED_HOST" == *.neon.tech ]] || echo "AVISO: o host não termina em .neon.tech — tem certeza que é o Neon?"

echo "== 2/4  Conexão DIRETA (sem -pooler) — usada só nas migrations =="
ask_secret "Cole a connection string DIRETA (oculta; Enter vazio = usar a mesma): "; DIRECT="$REPLY"
if [ -n "$DIRECT" ]; then
    parse_conn "$DIRECT"; DIRECT_URL="$JDBC_URL"
    [ "$DB_USER" = "$APP_USER" ] && [ "$DB_PASS" = "$APP_PASS" ] || die "usuário/senha da direta diferem da pooled — use o mesmo role"
    [[ "$DB_HOST" != *-pooler* ]] || echo "AVISO: a URL 'direta' contém -pooler; migrations podem falhar via pooler."
else
    DIRECT_URL="$POOLED_URL"
fi

echo "== 3/4  Testando a conexão =="
parse_conn "$POOLED"; test_conn "${BASH_REMATCH[5]:-}" "${BASH_REMATCH[8]:-sslmode=require}" \
    && echo "OK: conectou ao Neon." || die "não foi possível conectar com a string informada (confira senha/host)."

echo "== 4/4  Segredos do app =="
API_KEY="$(openssl rand -hex 32)"
read -rp "Usuário do dashboard [admin]: " DASH_USER < "${TTY:-/dev/tty}" || DASH_USER=""; DASH_USER="${DASH_USER:-admin}"
ask_secret "Senha do dashboard (oculta; Enter vazio = gerar uma): "; DASH_PASS="$REPLY"
GENERATED=0; if [ -z "$DASH_PASS" ]; then DASH_PASS="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)"; GENERATED=1; fi
echo "Opcional — controle via API do Neon no dashboard (Enter para pular):"
read -rp "NEON_PROJECT_ID: " NEON_PROJECT_ID < "${TTY:-/dev/tty}" || NEON_PROJECT_ID=""
NEON_API_KEY=""; [ -n "$NEON_PROJECT_ID" ] && { ask_secret "NEON_API_KEY (oculta): "; NEON_API_KEY="$REPLY"; }

mkdir -p "$APP_DIR"
[ -f "$ENV_FILE" ] && cp -p "$ENV_FILE" "$ENV_FILE.bak.$(date +%Y%m%d%H%M%S)"
TMP="$(mktemp "$APP_DIR/.env.XXXXXX")"
{
    echo "# Gerado por setup_token_control_env.sh em $(date -Is) — NÃO commitar"
    echo "SPRING_DATASOURCE_URL=$POOLED_URL"
    echo "SPRING_DATASOURCE_USERNAME=$APP_USER"
    echo "SPRING_DATASOURCE_PASSWORD=$APP_PASS"
    echo "NEON_DIRECT_URL=$DIRECT_URL"
    echo "TOKEN_CONTROL_API_KEY=$API_KEY"
    echo "DASHBOARD_USER=$DASH_USER"
    echo "DASHBOARD_PASSWORD=$DASH_PASS"
    echo "NEXT_PUBLIC_BASE_PATH=$BASE_PATH"
    echo "NEON_API_KEY=$NEON_API_KEY"
    echo "NEON_PROJECT_ID=$NEON_PROJECT_ID"
} > "$TMP"
chmod 600 "$TMP"; mv "$TMP" "$ENV_FILE"

echo
echo "Pronto: $ENV_FILE (permissão 600). Nenhum segredo foi impresso."
[ "$GENERATED" = 1 ] && echo "Senha do dashboard GERADA (anote agora, não será mostrada de novo): $DASH_PASS"
echo "Chave do collector: está em $ENV_FILE (linha TOKEN_CONTROL_API_KEY) — copie para a sua máquina com: grep TOKEN_CONTROL_API_KEY $ENV_FILE"
echo "Próximo passo (com confirmação): bash devops/scripts/deploy_token_control.sh"
