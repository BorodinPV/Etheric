#!/usr/bin/env bash
# Run Etheric k6 load test via Docker.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://host.docker.internal:8080}"
VUS="${VUS:-10}"
DURATION="${DURATION:-30s}"
SCENARIO="${SCENARIO:-all}"

echo "Etheric load test (k6 via Docker)"
echo "  BASE_URL=$BASE_URL  VUS=$VUS  DURATION=$DURATION  SCENARIO=$SCENARIO"
echo ""

if curl -sf --connect-timeout 3 "http://localhost:8080/health/live" >/dev/null; then
  echo "OK: Etheric responds on http://localhost:8080"
else
  echo "WARN: http://localhost:8080 not reachable. Start: ./scripts/dev.sh --no-rate-limit"
fi

echo "Checking $BASE_URL from Docker..."
if ! docker run --rm curlimages/curl:8.5.0 -sf --connect-timeout 3 "$BASE_URL/health/live" >/dev/null; then
  echo "ERROR: Docker cannot reach $BASE_URL"
  echo "On Linux try: BASE_URL=http://172.17.0.1:8080 ./scripts/loadtest/run.sh"
  exit 1
fi
echo "OK: Docker can reach Etheric"
echo ""

docker run --rm -i \
  -v "${SCRIPT_DIR}:/scripts" \
  -e "BASE_URL=${BASE_URL}" \
  -e "VUS=${VUS}" \
  -e "DURATION=${DURATION}" \
  -e "SCENARIO=${SCENARIO}" \
  grafana/k6 run /scripts/etheric.k6.js
