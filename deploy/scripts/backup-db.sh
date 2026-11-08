#!/usr/bin/env bash
#
# Nightly backup: pg_dump every service database, the RabbitMQ definitions, then prune old
# copies and optionally ship the run to Cloudflare R2 (or any S3-compatible store via rclone).
#
#   deploy/scripts/backup-db.sh
#
# Run from a systemd timer (deploy/systemd/ccmmd-backup.timer). Redis is not backed up: sessions
# are rebuildable and presence is TTL-scoped, so there is nothing there to lose.
set -euo pipefail

cd "$(dirname "$0")/../.."

ENV_FILE="deploy/.env.production"
# Read the handful of values this script needs rather than sourcing the file: a secret with shell
# metacharacters would otherwise break the script.
read_env() { sed -n "s/^$1=//p" "$ENV_FILE" | head -n 1; }
POSTGRES_USER="${POSTGRES_USER:-$(read_env POSTGRES_USER)}"
R2_BUCKET="${R2_BUCKET:-$(read_env R2_BUCKET)}"

COMPOSE="docker compose --env-file deploy/.env.production -f infra/docker-compose.prod.yml"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="deploy/backups/${STAMP}"
mkdir -p "$DEST"

DATABASES="auth_db game_db matchmaking_db notification_db analysis_db"

for db in $DATABASES; do
    echo "==> dumping ${db}"
    # Inside the container the unix socket is trusted, so no password is needed here.
    $COMPOSE exec -T postgres pg_dump --no-owner -U "${POSTGRES_USER}" -d "$db" \
        | gzip -9 > "${DEST}/${db}.sql.gz"
done

echo "==> exporting rabbitmq definitions"
$COMPOSE exec -T rabbitmq rabbitmqctl export_definitions - 2>/dev/null \
    | gzip -9 > "${DEST}/rabbit-definitions.json.gz" || echo "    (skipped: rabbitmq not reachable)"

# Retention: keep the 14 most recent runs.
ls -1dt deploy/backups/*/ 2>/dev/null | tail -n +15 | xargs -r rm -rf

if command -v rclone >/dev/null 2>&1 && [ -n "${R2_BUCKET:-}" ]; then
    echo "==> uploading to r2:${R2_BUCKET}/${STAMP}"
    rclone copy "${DEST}" "r2:${R2_BUCKET}/${STAMP}"
else
    echo "==> rclone/R2 not configured; backup kept locally at ${DEST}"
fi

echo "==> backup complete: ${DEST}"
