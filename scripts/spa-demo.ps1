# Run SPA demo (uses npm.cmd to avoid PowerShell execution policy issues).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..\examples\spa-demo

$npm = Join-Path $env:ProgramFiles "nodejs\npm.cmd"
if (-not (Test-Path $npm)) {
    $npmCmd = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($npmCmd) {
        $npm = $npmCmd.Source
    } else {
        throw "npm.cmd not found. Install Node.js LTS from https://nodejs.org and reopen the terminal."
    }
}

if (-not (Test-Path "node_modules")) {
    Write-Host "Running npm install..."
    & $npm install
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$esbuildBin = Join-Path $PWD "node_modules\@esbuild\win32-x64\esbuild.exe"
if (-not (Test-Path $esbuildBin)) {
    Write-Host "esbuild binary missing — approving install scripts and reinstalling..."
    & $npm approve-scripts --allow-scripts-pending 2>$null
    & $npm install
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "Starting Vite dev server on http://localhost:5173"
& $npm run dev @args
