# Start local PostgreSQL + Redis, then run Quarkus in dev mode.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

mvn -Pdev quarkus:dev @args
