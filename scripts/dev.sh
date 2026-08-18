#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d --wait

if [[ -x "./mvnw" ]]; then
  exec ./mvnw -Pdev quarkus:dev "$@"
fi

exec mvn -Pdev quarkus:dev "$@"
