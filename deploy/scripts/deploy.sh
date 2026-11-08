#!/usr/bin/env bash
#
# Deploy a given image tag and health-gate it. Runs on the VPS from the repo root (/opt/ccmmd).
#
#   TAG=<git-sha> deploy/scripts/deploy.sh
#
# Pulls the images CI pushed, recreates the stack, waits for the core services to report UP,
# and rolls back to the last known-good tag if they do not.
set -euo pipefail

cd "$(dirname "$0")/../.."          # repo root
COMPOSE="docker compose --env-file deploy/.env.production -f infra/docker-compose.prod.yml"
ENV_FILE="deploy/.env.production"
LAST_GOOD="deploy/.last-good-tag"

: "${TAG:?TAG env var (image tag / git sha) is required}"

# Read only the one value this script needs. Sourcing the whole file would clobber the caller's
# TAG with the file's default, and is fragile if a secret contains shell metacharacters.
DOMAIN="${DOMAIN:-$(sed -n 's/^DOMAIN=//p' "$ENV_FILE" | head -n 1)}"
: "${DOMAIN:?DOMAIN must be set in $ENV_FILE}"

wait_healthy() {
    local label="$1" svc="$2" port="$3"
    for _ in $(seq 1 60); do
        if $COMPOSE exec -T "$svc" wget -qO- "http://localhost:${port}/actuator/health" 2>/dev/null \
                | grep -q '"status":"UP"'; then
            echo "    ${label}: UP"
            return 0
        fi
        sleep 5
    done
    echo "    ${label}: NOT HEALTHY" >&2
    return 1
}

rollback() {
    if [ -f "$LAST_GOOD" ]; then
        local previous
        previous="$(cat "$LAST_GOOD")"
        echo "!! deploy of ${TAG} failed — rolling back to ${previous}" >&2
        TAG="$previous" $COMPOSE up -d --remove-orphans
        $COMPOSE restart edge-nginx
    else
        echo "!! deploy of ${TAG} failed and there is no previous good tag to roll back to" >&2
    fi
}

echo "==> pulling images for tag ${TAG}"
$COMPOSE pull

echo "==> starting stack"
$COMPOSE up -d --remove-orphans

# nginx resolves upstream hostnames at start; restart it so it points at the recreated gateway.
$COMPOSE restart edge-nginx

echo "==> waiting for core services"
if ! wait_healthy auth-service auth-service 8081 \
   || ! wait_healthy game-service game-service 8082 \
   || ! wait_healthy api-gateway api-gateway 8080; then
    rollback
    exit 1
fi

echo "==> checking the public edge"
for _ in $(seq 1 12); do
    if curl -fsS "https://${DOMAIN}/" -o /dev/null 2>/dev/null; then
        echo "    edge: OK (https://${DOMAIN}/)"
        break
    fi
    sleep 5
done

echo "${TAG}" > "$LAST_GOOD"
echo "==> deploy of ${TAG} complete"
