#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

API_URL="${API_URL:-http://localhost:${API_HOST_PORT:-3003}}"
GCS_URL="${GCS_URL:-http://localhost:${GCS_PORT:-3000}}"

failures=0

report() {
  local outcome=$1 name=$2 detail=${3:-}
  if [[ $outcome == pass ]]; then
    printf '  [PASS] %s\n' "$name"
  else
    printf '  [FAIL] %s %s\n' "$name" "$detail" >&2
    failures=$((failures + 1))
  fi
}

expect_status() {
  local name=$1 url=$2 expected=$3
  local actual
  actual=$(curl --silent --output /dev/null --write-out '%{http_code}' "$url" || true)
  if [[ $actual == "$expected" ]]; then
    report pass "$name"
  else
    report fail "$name" "expected $expected, got $actual from $url"
  fi
}

expect_json_field() {
  local name=$1 url=$2 field=$3 expected=$4
  local actual
  actual=$(curl --silent "$url" | python3 -c "import json,sys; print(json.load(sys.stdin).get('$field',''))" 2>/dev/null || true)
  if [[ $actual == "$expected" ]]; then
    report pass "$name"
  else
    report fail "$name" "expected $field=$expected, got '$actual'"
  fi
}

expect_container_healthy() {
  local name=$1 service=$2
  local state
  state=$(docker compose ps --format '{{.Service}} {{.Health}}' | awk -v s="$service" '$1 == s {print $2}')
  if [[ $state == healthy ]]; then
    report pass "$name"
  else
    report fail "$name" "health='${state:-missing}'"
  fi
}

printf 'bringing the stack up\n'
docker compose up --build --detach

printf 'waiting for readiness\n'
bash docker/wait-for-readiness.sh

printf 'running smoke checks\n'
expect_container_healthy 'mysql container is healthy' mysql
expect_container_healthy 'gate-control-system container is healthy' gate-control-system
expect_container_healthy 'api container is healthy' api
expect_status 'GET /health returns 200' "$API_URL/health" 200
expect_status 'GET /health/readiness returns 200' "$API_URL/health/readiness" 200
expect_json_field 'readiness reports UP' "$API_URL/health/readiness" status UP
expect_status 'GCS GET /garage returns 200' "$GCS_URL/garage" 200

if ((failures > 0)); then
  printf '\nsmoke failed with %s failing check(s)\n' "$failures" >&2
  exit 1
fi

printf '\nsmoke passed\n'
