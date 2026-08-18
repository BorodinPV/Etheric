#!/usr/bin/env bash
# Usage:
#   ./scripts/dev.sh
#   ./scripts/dev.sh --no-rate-limit
#   ./scripts/dev.sh -Detheric.rate-limit.enabled=false
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d --wait

MVN_ARGS=(-Pdev -Dquarkus.analytics.disabled=true)
QUARKUS_ARGS=()
for arg in "$@"; do
  if [[ "$arg" == "--no-rate-limit" ]]; then
    MVN_ARGS+=(-Detheric.rate-limit.enabled=false)
  else
    QUARKUS_ARGS+=("$arg")
  fi
done

if [[ -x "./mvnw" ]]; then
  exec ./mvnw "${MVN_ARGS[@]}" quarkus:dev "${QUARKUS_ARGS[@]}"
fi

exec mvn "${MVN_ARGS[@]}" quarkus:dev "${QUARKUS_ARGS[@]}"
