#!/bin/bash
# =================================================================
# Backup lógico do banco Neon (PostgreSQL) — PortalCursos.NG
# Requer: pg_dump instalado no host que executa o script (VPS ou local)
# e a variável SPRING_DATASOURCE_URL exportada (formato jdbc:postgresql://...
# ou postgresql://... aceito pelo pg_dump) + SPRING_DATASOURCE_USERNAME/PASSWORD.
# =================================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/portalcursos}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
DATE=$(date +"%Y-%m-%d_%H-%M-%S")

ENV_FILE="${ENV_FILE:-$(dirname "$0")/../.env}"
if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
fi

: "${SPRING_DATASOURCE_URL:?Defina SPRING_DATASOURCE_URL (jdbc:postgresql://host/db?sslmode=require)}"
: "${SPRING_DATASOURCE_USERNAME:?Defina SPRING_DATASOURCE_USERNAME}"
: "${SPRING_DATASOURCE_PASSWORD:?Defina SPRING_DATASOURCE_PASSWORD}"

# Converte jdbc:postgresql://host/db?params -> postgresql://host/db?params
PG_URL="${SPRING_DATASOURCE_URL#jdbc:}"

mkdir -p "$BACKUP_DIR"

echo "💾 Iniciando backup lógico do Neon..."
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" pg_dump \
    --host="$(echo "$PG_URL" | sed -E 's#postgresql://([^/:@]+@)?([^/:]+).*#\2#')" \
    --username="$SPRING_DATASOURCE_USERNAME" \
    --dbname="$(echo "$PG_URL" | sed -E 's#.*/([^/?]+).*#\1#')" \
    --format=custom \
    --no-owner --no-privileges \
    --file="$BACKUP_DIR/neon_$DATE.dump"

echo "✅ Backup concluído em $BACKUP_DIR/neon_$DATE.dump"

find "$BACKUP_DIR" -type f -name "neon_*.dump" -mtime +"$RETENTION_DAYS" -delete
echo "🧹 Backups com mais de $RETENTION_DAYS dias removidos."

echo "ℹ️  Lembrete: Neon oferece Point-in-Time Restore nativo (planos pagos) como"
echo "   segunda camada de proteção — este script cobre backup lógico portátil"
echo "   (permite restaurar em qualquer instância PostgreSQL, não só Neon)."
