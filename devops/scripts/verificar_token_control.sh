#!/bin/bash
# Verificação completa do Controle de Tokens na VPS — só LEITURA (não altera nada, não imprime segredos).
# Lê tudo que precisa do próprio .env (nenhuma senha digitada) e imprime UM relatório com PASS/FAIL no fim.
# Uso:  bash verificar_token_control.sh        (relatório também salvo em /root/verificacao.txt)
# Variáveis opcionais: APP_DIR, PUBLIC_URL, BACKEND_URL, PORTAL_URL, CONTAINER
set -uo pipefail

APP_DIR="${APP_DIR:-/var/www/token-control}"
ENV_FILE="$APP_DIR/.env"
PUBLIC_URL="${PUBLIC_URL:-https://xavierbr-vps.tech/tokencontrol}"
BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8190}"
PORTAL_URL="${PORTAL_URL-https://xavierbr-vps.tech/portalcursos.ng}"
CONTAINER="${CONTAINER:-token-control-backend-1}"
OUT="${OUT:-/root/verificacao.txt}"

FAILS=0
report() { printf '%s\n' "$*"; }
ok()   { report "  [OK]   $*"; }
bad()  { report "  [FALHA] $*"; FAILS=$((FAILS+1)); }
skip() { report "  [pulado] $*"; }
envval() { grep -m1 "^$1=" "$ENV_FILE" 2>/dev/null | cut -d= -f2-; }
mask() { local v="$1"; [ -n "$v" ] && printf '%s...%s' "${v:0:4}" "${v: -4}" || printf '(vazia)'; }
code() { curl -sk -o /dev/null -w '%{http_code}' --max-time 20 "$@" 2>/dev/null || echo 000; }

{
report "=== Verificação do Controle de Tokens — $(date -Is) ==="

report; report "1) Configuração (.env)"
if [ ! -f "$ENV_FILE" ]; then bad "$ENV_FILE não existe"; else
  perm=$(stat -c %a "$ENV_FILE")
  [ "$perm" = "600" ] && ok "permissão do .env = 600" || bad "permissão do .env = $perm (esperado 600)"
  KEY=$(envval TOKEN_CONTROL_API_KEY)
  if [[ "$KEY" =~ ^[0-9a-f]{64}$ ]]; then ok "TOKEN_CONTROL_API_KEY: 64 hex, $(mask "$KEY")"; else bad "TOKEN_CONTROL_API_KEY ausente ou fora do formato (64 hex)"; fi
  DIRECT=$(envval NEON_DIRECT_URL); POOLED=$(envval SPRING_DATASOURCE_URL)
  [[ "$POOLED" == *-pooler* ]] && ok "URL da aplicação é pooled" || bad "SPRING_DATASOURCE_URL sem -pooler"
  [[ -n "$DIRECT" && "$DIRECT" != *-pooler* ]] && ok "URL das migrations é direta (sem -pooler)" || bad "NEON_DIRECT_URL ausente ou com -pooler"
  [ -n "$(envval NEON_PROJECT_ID)" ] && { [[ "$(envval NEON_PROJECT_ID)" == br-* ]] && bad "NEON_PROJECT_ID começa com 'br-' (isso é ID de BRANCH, não de projeto)" || ok "NEON_PROJECT_ID parece de projeto"; } || skip "NEON_PROJECT_ID vazio (card de consumo do Neon desligado)"
fi

report; report "2) Containers"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  for c in token-control-backend-1 token-control-frontend-1; do
    st=$(docker inspect -f '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}sem-healthcheck{{end}}' "$c" 2>/dev/null)
    [[ "$st" == running/* ]] && ok "$c: $st" || bad "$c: ${st:-não encontrado}"
  done
  CK=$(docker exec "$CONTAINER" printenv TOKEN_CONTROL_API_KEY 2>/dev/null)
  if [ -n "${KEY:-}" ] && [ -n "$CK" ]; then
    [ "$KEY" = "$CK" ] && ok "container usa a MESMA chave do .env ($(mask "$CK"))" || bad "container usa chave DIFERENTE do .env (container $(mask "$CK") x .env $(mask "$KEY")) — refaça o deploy"
  else skip "não foi possível comparar a chave do container"; fi
else skip "docker indisponível neste ambiente"; fi

report; report "3) Chave aceita pelo app (POST com lote vazio: 400 = aceita, 401 = recusada)"
if [ -n "${KEY:-}" ]; then
  for pair in "backend direto|$BACKEND_URL/api/tokens/usage" "público (Traefik/Next)|$PUBLIC_URL/api/tokens/usage"; do
    name="${pair%%|*}"; url="${pair#*|}"
    c=$(code -X POST -H "X-API-Key: $KEY" -H 'Content-Type: application/json' -d '{"entries":[]}' "$url")
    case "$c" in 400) ok "$name -> 400 (chave aceita)";; 401) bad "$name -> 401 (chave RECUSADA)";; *) bad "$name -> HTTP $c";; esac
  done
  c=$(code -X POST -H "X-API-Key: chave-errada" -H 'Content-Type: application/json' -d '{"entries":[]}' "$PUBLIC_URL/api/tokens/usage")
  [ "$c" = "401" ] && ok "chave errada é recusada (401) — proteção ativa" || bad "chave errada deu HTTP $c (esperado 401)"
else skip "sem chave para testar"; fi

report; report "4) Dashboard (login Basic com as credenciais do .env)"
DU=$(envval DASHBOARD_USER); DP=$(envval DASHBOARD_PASSWORD)
c=$(code "$PUBLIC_URL"); [ "$c" = "401" ] && ok "sem login -> 401 (protegido)" || bad "sem login -> HTTP $c (esperado 401)"
if [ -n "$DP" ]; then
  for p in "" "/api/tokens/summary" "/api/tokens/month"; do
    c=$(printf 'user = "%s:%s"\n' "$DU" "$DP" | curl -sk -K - -o /dev/null -w '%{http_code}' --max-time 20 "$PUBLIC_URL$p" 2>/dev/null || echo 000)
    [ "$c" = "200" ] && ok "login + ${p:-/} -> 200" || bad "login + ${p:-/} -> HTTP $c"
  done
  DB=$(printf 'user = "%s:%s"\n' "$DU" "$DP" | curl -sk -K - --max-time 20 "$PUBLIC_URL/api/tokens/db" 2>/dev/null)
  SUM=$(echo "$DB" | python3 -c "import sys,json; d=json.load(sys.stdin); print(('OK' if d['connected'] and d['migrations']['status']=='OK' else 'FALHA')+'|connected=%s migrations=%s v%s pooled=%s latencia=%sms'%(d['connected'],d['migrations']['status'],d['migrations']['latest'],d['endpoint']['pooled'],d['latencyMs']))" 2>/dev/null || echo "FALHA|resposta inválida de /db")
  [ "${SUM%%|*}" = "OK" ] && ok "Neon: ${SUM#*|}" || bad "Neon: ${SUM#*|}"
  MS=$(printf 'user = "%s:%s"\n' "$DU" "$DP" | curl -sk -K - --max-time 20 "$PUBLIC_URL/api/tokens/month" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print('mensagens no mês: %s | usado: %s | limite: %s (%s)'%(d['totals']['messages'],d['used'],d['limit'],'estimado' if d['limitEstimated'] else 'configurado'))" 2>/dev/null)
  [ -n "$MS" ] && ok "$MS" || bad "resposta inválida de /month"
else skip "DASHBOARD_PASSWORD ausente no .env"; fi

report; report "5) PortalCursos.NG (não pode ter sido afetado)"
if [ -n "$PORTAL_URL" ]; then c=$(code "$PORTAL_URL"); [ "$c" = "200" ] && ok "portalcursos -> 200" || bad "portalcursos -> HTTP $c"; else skip "PORTAL_URL vazio"; fi

report; report "=== RESULTADO: $([ "$FAILS" -eq 0 ] && echo "TUDO OK" || echo "$FAILS FALHA(S) — veja as linhas [FALHA] acima") ==="
[ "$FAILS" -eq 0 ]   # status do bloco (o contador vive neste subshell por causa do tee)
} 2>&1 | tee "$OUT"
exit "${PIPESTATUS[0]}"
