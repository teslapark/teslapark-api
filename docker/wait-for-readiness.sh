#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:${API_HOST_PORT:-3003}}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-120}"

printf 'waiting for %s/health/readiness (timeout %ss)\n' "$API_URL" "$TIMEOUT_SECONDS"

deadline=$((SECONDS + TIMEOUT_SECONDS))
while ((SECONDS < deadline)); do
  if curl --fail --silent --output /dev/null "$API_URL/health/readiness"; then
    printf 'api is ready at %s\n' "$API_URL"
    exit 0
  fi
  sleep 2
done

printf 'api did not become ready within %ss\n' "$TIMEOUT_SECONDS" >&2
docker compose ps
docker compose logs --tail=50 api >&2
exit 1
