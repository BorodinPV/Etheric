#!/usr/bin/env bash
# Usage:
#   ./scripts/macos/dev.sh
#   ./scripts/macos/dev.sh --no-rate-limit
#   ./scripts/macos/dev.sh -Detheric.rate-limit.enabled=false
set -euo pipefail

cd "$(dirname "$0")/../.."

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

# Bash 3.2 (macOS /bin/bash) treats empty "${arr[@]}" as unbound under `set -u`.
mvn_cmd=./mvnw
if [[ ! -x "$mvn_cmd" ]]; then
  mvn_cmd=mvn
fi

if [[ ${#QUARKUS_ARGS[@]} -gt 0 ]]; then
  exec "$mvn_cmd" "${MVN_ARGS[@]}" quarkus:dev "${QUARKUS_ARGS[@]}"
fi

exec "$mvn_cmd" "${MVN_ARGS[@]}" quarkus:dev
