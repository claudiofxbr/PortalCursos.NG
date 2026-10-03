#!/bin/bash
# Configura (ou desliga) a API de gestão do Neon no .env do Controle de Tokens.
# Altera SOMENTE NEON_PROJECT_ID e NEON_API_KEY — não mexe na TOKEN_CONTROL_API_KEY nem nas credenciais do banco.
# Testa a combinação ID+chave contra o Neon ANTES de gravar; se falhar, não grava nada.
#
# Uso:  bash configurar_neon_api.sh            (pergunta o Project ID e a chave; a chave é oculta)
#       bash configurar_neon_api.sh --desligar (limpa as duas variáveis; a Torre mostra "API do Neon desligada")
set -uo pipefail
umask 077

APP_DIR="${APP_DIR:-/var/www/token-control}"
ENV_FILE="$APP_DIR/.env"
NEON_API_BASE="${NEON_API_BASE:-https://console.neon.tech/api/v2}"
COMPOSE="${COMPOSE:-docker compose -f $APP_DIR/src/token-control/docker-compose.yml --env-file $ENV_FILE}"

die() { echo "ERRO: $*" >&2; exit 1; }
[ -f "$ENV_FILE" ] || die "$ENV_FILE não existe (rode setup_token_control_env.sh antes)"

setvar() { # NOME VALOR -> troca (ou acrescenta) a linha NOME=... preservando todo o resto; sem escapar nada
  local tmp; tmp="$(mktemp "$APP_DIR/.env.XXXXXX")"
  NAME="$1" VALUE="$2" awk 'BEGIN{n=ENVIRON["NAME"]; v=ENVIRON["VALUE"]; d=0}
    index($0, n"=")==1 {print n"="v; d=1; next} {print} END{if(!d) print n"="v}' "$ENV_FILE" > "$tmp" || die "falha ao preparar o novo .env"
  chmod 600 "$tmp"; mv "$tmp" "$ENV_FILE"
}

apply() { # ID KEY
  local bak="$ENV_FILE.bak.$(date +%Y%m%d%H%M%S)"
  cp -p "$ENV_FILE" "$bak" || die "não consegui criar o backup do .env"
  setvar NEON_PROJECT_ID "$1"; setvar NEON_API_KEY "$2"
  chmod 600 "$ENV_FILE"
  echo "Gravado em $ENV_FILE (backup: $bak — contém a configuração anterior; apague quando quiser)."
  if [ "${SKIP_RECREATE:-0}" = 1 ]; then echo "(recriação dos containers pulada)"; return; fi
  echo "Recriando os containers para carregar o novo .env..."
  $COMPOSE up -d || die "falha ao recriar os containers"
}

if [ "${1:-}" = "--desligar" ]; then apply "" ""; echo "API do Neon desligada."; exit 0; fi

read -rp "Project ID do Neon (Project settings > General; formato nome-nome-12345678, NÃO começa com br-): " ID < "${TTY:-/dev/tty}" || read -r ID
ID="$(echo "$ID" | tr -d '[:space:]')"
[[ "$ID" == br-* ]] && die "isso é ID de BRANCH (br-…). Use o Project ID, em Project settings > General."
[[ "$ID" =~ ^[a-z]+(-[a-z]+)+-[0-9]+$ ]] || die "formato inesperado: '$ID'. Esperado algo como quiet-art-12345678."
read -rsp "NEON_API_KEY (Account settings > API keys; oculta): " KEY < "${TTY:-/dev/tty}" || read -rsp "NEON_API_KEY: " KEY; echo
KEY="$(echo "$KEY" | tr -d '[:space:]')"
[ -n "$KEY" ] || die "chave vazia"

echo "Testando no Neon (somente leitura)..."
CODE=$(printf 'header = "Authorization: Bearer %s"\n' "$KEY" | curl -s -K - -o /dev/null -w '%{http_code}' --max-time 20 "$NEON_API_BASE/projects/$ID" || echo 000)
case "$CODE" in
  200) echo "OK: o Neon aceitou a chave e encontrou o projeto." ;;
  401) die "o Neon recusou a CHAVE (HTTP 401). Crie uma nova em Account settings > API keys e confira se copiou inteira. Nada foi gravado." ;;
  403) die "a chave não tem acesso a esse projeto (HTTP 403). Use uma chave da organização/conta dona do projeto. Nada foi gravado." ;;
  404) die "o Neon não achou o projeto '$ID' (HTTP 404). Confira o Project ID. Nada foi gravado." ;;
  000) die "não consegui falar com o Neon (rede). Nada foi gravado." ;;
  *)   die "resposta inesperada do Neon: HTTP $CODE. Nada foi gravado." ;;
esac
apply "$ID" "$KEY"
echo "Pronto. Confira com: bash /root/verificar_token_control.sh"
