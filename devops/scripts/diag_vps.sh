#!/bin/bash
# diag_vps.sh - Diagnostico focado no erro do Nginx
# PortalCursos.NG | xavierbr-vps.tech

echo "==== 1. QUEM USA PORTA 80 E 443 ===="
ss -tlnp 2>/dev/null | grep -E ':(80|443) ' || echo "(nada nas portas 80/443)"

echo ""
echo "==== 2. CONTAINERS DOCKER COM PORTAS ===="
docker ps --format "{{.Names}} | {{.Status}} | {{.Ports}}" 2>/dev/null || echo "(docker indisponivel)"

echo ""
echo "==== 3. STATUS NGINX ===="
systemctl status nginx --no-pager -l 2>&1 | head -40

echo ""
echo "==== 4. LOGS NGINX (journalctl) ===="
journalctl -u nginx -n 50 --no-pager 2>&1

echo ""
echo "==== 5. NGINX -T (teste config) ===="
nginx -t 2>&1 || true

echo ""
echo "==== 6. TODOS ARQUIVOS EM sites-enabled ===="
ls -la /etc/nginx/sites-enabled/ 2>/dev/null || echo "(diretorio nao existe)"
echo ""
for f in /etc/nginx/sites-enabled/*; do
    [ -f "$f" ] || [ -L "$f" ] || continue
    echo "--- $f ---"
    cat "$f" 2>/dev/null | head -30
    echo ""
done

echo ""
echo "==== 7. /etc/nginx/nginx.conf (primeiras 30 linhas) ===="
head -30 /etc/nginx/nginx.conf 2>/dev/null || echo "(nao encontrado)"

echo ""
echo "==== 8. TRAEFIK CONTAINERS ===="
docker ps -a --filter name=traefik --format "{{.Names}} | {{.Status}} | {{.Ports}}" 2>/dev/null || echo "(sem traefik)"

echo ""
echo "==== 9. EASYPANEL CONTAINERS ===="
docker ps -a --filter name=easypanel --format "{{.Names}} | {{.Status}} | {{.Ports}}" 2>/dev/null || echo "(sem easypanel)"

echo ""
echo "==== 10. QUALQUER PROCESSO NA PORTA 80 (fuser) ===="
fuser 80/tcp 2>/dev/null && echo "" || echo "(fuser: porta 80 livre ou fuser nao disponivel)"
fuser 443/tcp 2>/dev/null && echo "" || echo "(fuser: porta 443 livre ou fuser nao disponivel)"

echo ""
echo "==== FIM ===="
