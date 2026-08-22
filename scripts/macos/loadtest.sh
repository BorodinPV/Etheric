#!/usr/bin/env bash
# Run Etheric k6 load test via Docker.
#
# Usage:
#   ./scripts/macos/loadtest.sh
#   ./scripts/macos/loadtest.sh --vus 20 --duration 1m --scenario refresh
#
# Start Etheric first (rate limit off recommended):
#   ./scripts/macos/dev.sh --no-rate-limit
#
# Env overrides: BASE_URL, VUS, DURATION, SCENARIO
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
K6_DIR="$(cd "$SCRIPT_DIR/../loadtest" && pwd)"

BASE_URL="${BASE_URL:-http://host.docker.internal:8080}"
VUS="${VUS:-10}"
DURATION="${DURATION:-30s}"
SCENARIO="${SCENARIO:-all}"

usage() {
  echo "Usage: $0 [--vus N] [--duration 30s] [--scenario all|refresh|introspect|authorize|public] [--base-url URL]"
}

need_value() {
  if [[ $# -lt 2 ]]; then
    echo "Missing value for $1" >&2
    usage >&2
    exit 1
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      need_value "$@"
      BASE_URL="$2"
      shift 2
      ;;
    --vus)
      need_value "$@"
      VUS="$2"
      shift 2
      ;;
    --duration)
      need_value "$@"
      DURATION="$2"
      shift 2
      ;;
    --scenario)
      need_value "$@"
      SCENARIO="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

echo "Etheric load test (k6 via Docker)"
echo "  BASE_URL=$BASE_URL  VUS=$VUS  DURATION=$DURATION  SCENARIO=$SCENARIO"
echo ""

host_ok=0
if curl -sf --connect-timeout 3 "http://localhost:8080/health/live" >/dev/null; then
  host_ok=1
  echo "OK: Etheric responds on http://localhost:8080"
else
  echo "WARN: http://localhost:8080 not reachable from host."
  echo "      Start Etheric: ./scripts/macos/dev.sh --no-rate-limit"
fi

echo "Checking $BASE_URL from Docker..."
if ! docker run --rm curlimages/curl:8.5.0 -sf --connect-timeout 3 "$BASE_URL/health/live" >/dev/null; then
  echo "ERROR: Docker cannot reach $BASE_URL"
  if [[ "$host_ok" -eq 1 ]]; then
    echo "Etheric runs on localhost but not via host.docker.internal."
    echo "Try: ./scripts/macos/loadtest.sh --base-url http://host.docker.internal:8080"
    echo "On Linux try: ./scripts/macos/loadtest.sh --base-url http://172.17.0.1:8080"
    echo "Or install k6 locally and run: k6 run scripts/loadtest/etheric.k6.js -e BASE_URL=http://localhost:8080"
  else
    echo "Start Etheric first, then rerun load test."
  fi
  exit 1
fi
echo "OK: Docker can reach Etheric"
echo ""

docker run --rm -i \
  -v "${K6_DIR}:/scripts" \
  -e "BASE_URL=${BASE_URL}" \
  -e "VUS=${VUS}" \
  -e "DURATION=${DURATION}" \
  -e "SCENARIO=${SCENARIO}" \
  grafana/k6 run /scripts/etheric.k6.js
