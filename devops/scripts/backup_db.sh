#!/bin/bash

# =================================================================
# OMEGA-SUPREME: Database Backup Script
# =================================================================

DB_NAME="u123456789_portal_cursos"
DB_USER="u123456789_admin"
BACKUP_DIR="/home/$(whoami)/backups/db"
DATE=$(date +"%Y-%m-%d_%H-%M-%S")

mkdir -p $BACKUP_DIR

echo "💾 Iniciando backup do banco de dados $DB_NAME..."

mysqldump -u $DB_USER -p $DB_NAME > $BACKUP_DIR/backup_$DATE.sql

echo "✅ Backup concluído em $BACKUP_DIR/backup_$DATE.sql"

# Remover backups com mais de 30 dias
find $BACKUP_DIR -type f -mtime +30 -name "*.sql" -delete
echo "🧹 Backups antigos removidos."
