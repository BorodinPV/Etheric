#!/usr/bin/env bash
# Run SPA demo (Vite) on http://localhost:5173
set -euo pipefail

cd "$(dirname "$0")/../../examples/spa-demo"

if ! command -v npm >/dev/null 2>&1; then
  echo "npm not found. Install Node.js LTS from https://nodejs.org and reopen the terminal." >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  echo "Running npm install..."
  npm install
fi

esbuild_ok=0
if [[ -x node_modules/.bin/esbuild ]]; then
  esbuild_ok=1
elif [[ -x node_modules/@esbuild/linux-x64/bin/esbuild ]]; then
  esbuild_ok=1
elif [[ -x node_modules/@esbuild/linux-arm64/bin/esbuild ]]; then
  esbuild_ok=1
fi

if [[ "$esbuild_ok" -eq 0 ]]; then
  echo "esbuild binary missing - reinstalling..."
  npm install
fi

echo "Starting Vite dev server on http://localhost:5173"
exec npm run dev -- "$@"
