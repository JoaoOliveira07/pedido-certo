#!/usr/bin/env bash
# Backup diário do Postgres, com 14 dias de retenção.
# Agende como o usuário de deploy (crontab -e):
#   15 3 * * * /opt/jopes/backup.sh >> /opt/jopes/backups/backup.log 2>&1
#
# Para guardar fora da VPS também, sincronize a pasta com rclone para um bucket.
set -euo pipefail

cd /opt/jopes
STAMP=$(date +%Y-%m-%d_%H%M)
FILE="backups/pedidocerto-$STAMP.sql.gz"

docker compose exec -T postgres pg_dump -U pedidocerto -d pedidocerto --no-owner | gzip > "$FILE"
find backups -name 'pedidocerto-*.sql.gz' -mtime +14 -delete
echo "$(date -Is) backup ok: $FILE ($(du -h "$FILE" | cut -f1))"
