#!/usr/bin/env bash
# Run confidential-client demo (Node BFF) on http://localhost:5174
set -euo pipefail

cd "$(dirname "$0")/../../examples/confidential-demo"

if ! command -v node >/dev/null 2>&1; then
  echo "node not found. Install Node.js LTS from https://nodejs.org and reopen the terminal." >&2
  exit 1
fi

echo "Starting confidential demo BFF on http://localhost:5174"
exec node server.js "$@"
