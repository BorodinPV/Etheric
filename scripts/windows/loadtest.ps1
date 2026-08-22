# Run Etheric k6 load test via Docker.
#
# Usage:
#   .\scripts\windows\loadtest.ps1
#   .\scripts\windows\loadtest.ps1 -Vus 20 -Duration 1m -Scenario refresh
#
# Start Etheric first (rate limit off recommended):
#   .\scripts\windows\dev.ps1 -DisableRateLimit

param(
    [string]$BaseUrl = "http://host.docker.internal:8080",
    [int]$Vus = 10,
    [string]$Duration = "30s",
    [string]$Scenario = "all"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$k6Dir = (Resolve-Path (Join-Path $scriptDir "..\loadtest")).Path

Write-Host "Etheric load test (k6 via Docker)"
Write-Host "  BASE_URL=$BaseUrl  VUS=$Vus  DURATION=$Duration  SCENARIO=$Scenario"
Write-Host ""

$hostOk = $false
try {
    $null = Invoke-WebRequest -Uri "http://localhost:8080/health/live" -UseBasicParsing -TimeoutSec 3
    $hostOk = $true
    Write-Host "OK: Etheric responds on http://localhost:8080"
} catch {
    Write-Host "WARN: http://localhost:8080 not reachable from Windows host."
    Write-Host "      Start Etheric: .\scripts\windows\dev.ps1 -DisableRateLimit"
}

Write-Host "Checking $BaseUrl from Docker..."
docker run --rm curlimages/curl:8.5.0 -sf --connect-timeout 3 "$BaseUrl/health/live" | Out-Null
$dockerOk = ($LASTEXITCODE -eq 0)

if (-not $dockerOk) {
    Write-Host ""
    Write-Host "ERROR: Docker cannot reach $BaseUrl"
    if ($hostOk) {
        Write-Host "Etheric runs on localhost but not via host.docker.internal."
        Write-Host "Try: .\scripts\windows\loadtest.ps1 -BaseUrl http://host.docker.internal:8080"
        Write-Host "Or install k6 locally and run: k6 run scripts/loadtest/etheric.k6.js -e BASE_URL=http://localhost:8080"
    } else {
        Write-Host "Start Etheric first, then rerun load test."
    }
    exit 1
}
Write-Host "OK: Docker can reach Etheric"
Write-Host ""

docker run --rm -i `
    -v "${k6Dir}:/scripts" `
    -e "BASE_URL=$BaseUrl" `
    -e "VUS=$Vus" `
    -e "DURATION=$Duration" `
    -e "SCENARIO=$Scenario" `
    grafana/k6 run /scripts/etheric.k6.js

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
