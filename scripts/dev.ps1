# Start local PostgreSQL + Redis, then run Quarkus in dev mode.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

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

& $mvn -Pdev "-Dquarkus.analytics.disabled=true" quarkus:dev @args
