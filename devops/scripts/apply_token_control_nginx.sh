#!/bin/bash
# Expõe o Controle de Tokens em https://xavierbr-vps.tech/tokencontrol (nginx do host).
# Mudança ADITIVA e reversível no site do PortalCursos.NG: 1 linha "include" + 1 arquivo de extra.
# Gate: nginx -t; se falhar, restaura o backup e NÃO recarrega. Rode só com confirmação explícita.
set -euo pipefail

SITE="/etc/nginx/sites-available/portalcursos"
EXTRA_DIR="/etc/nginx/portalcursos-extra.d"
SNIPPET_SRC="${SNIPPET_SRC:-/var/www/token-control/src/token-control/nginx-snippet.conf}"
INCLUDE='    include /etc/nginx/portalcursos-extra.d/*.conf;'
BACKUP="/root/nginx-portalcursos.bak.$(date +%Y%m%d%H%M%S)"

[ "$(id -u)" -eq 0 ] || { echo "ERRO: rode como root"; exit 1; }
[ -s "$SITE" ] || { echo "ERRO: $SITE ausente ou vazio (ver incidente de 2026-09-30 no CLAUDE.md) — abortando sem alterar"; exit 1; }
[ -f "$SNIPPET_SRC" ] || { echo "ERRO: $SNIPPET_SRC não existe (rode deploy_token_control.sh antes)"; exit 1; }

cp -p "$SITE" "$BACKUP"
mkdir -p "$EXTRA_DIR"
cp "$SNIPPET_SRC" "$EXTRA_DIR/token-control.conf"

if ! grep -q 'portalcursos-extra.d' "$SITE"; then
  grep -q 'ssl_prefer_server_ciphers off;' "$SITE" || { echo "ERRO: âncora não encontrada em $SITE; nada alterado além do snippet"; rm -f "$EXTRA_DIR/token-control.conf"; exit 1; }
  sed -i "0,/ssl_prefer_server_ciphers off;/s##ssl_prefer_server_ciphers off;\n\n$INCLUDE#" "$SITE"
fi

revert() { cp -p "$BACKUP" "$SITE"; rm -f "$EXTRA_DIR/token-control.conf"; nginx -t || true; }

if nginx -t; then
  # reload pode falhar por motivo alheio ao config (ex.: pidfile vazio): tenta o systemd; se nada recarregar, REVERTE
  if nginx -s reload || systemctl reload nginx; then
    echo "OK: nginx recarregado. Backup do site: $BACKUP"
    echo "Rollback: cp -p $BACKUP $SITE && rm -f $EXTRA_DIR/token-control.conf && nginx -t && nginx -s reload"
  else
    echo "ERRO: não foi possível recarregar o nginx — revertendo o config (nada fica pendente para o próximo reload)"
    revert; exit 1
  fi
else
  echo "ERRO: nginx -t falhou — restaurando backup, sem reload"
  revert; exit 1
fi
