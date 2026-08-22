# Start local PostgreSQL + Redis, then run Quarkus in dev mode.
#
# Usage:
#   .\scripts\windows\dev.ps1
#   .\scripts\windows\dev.ps1 -DisableRateLimit
#   .\scripts\windows\dev.ps1 "-Detheric.rate-limit.enabled=false"
param(
    [switch]$DisableRateLimit
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..\..

docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$mvn = Join-Path $PWD "mvnw.cmd"
if (-not (Test-Path $mvn)) {
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnCmd) {
        $mvn = $mvnCmd.Source
    } else {
        throw "Maven not found. Use .\mvnw.cmd (run from repo root) or install Maven and add it to PATH."
    }
}

$mvnArgs = @("-Pdev", "-Dquarkus.analytics.disabled=true")
if ($DisableRateLimit) {
    $mvnArgs += "-Detheric.rate-limit.enabled=false"
}

& $mvn @mvnArgs quarkus:dev @args
