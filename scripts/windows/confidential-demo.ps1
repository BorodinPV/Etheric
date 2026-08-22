# Run confidential-client demo (Node BFF, secret stays on the server).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..\..\examples\confidential-demo")

$node = Join-Path $env:ProgramFiles "nodejs\node.exe"
if (-not (Test-Path $node)) {
    $nodeCmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($nodeCmd) {
        $node = $nodeCmd.Source
    } else {
        throw "node.exe not found. Install Node.js LTS from https://nodejs.org and reopen the terminal."
    }
}

Write-Host "Starting confidential demo BFF on http://localhost:5174"
& $node server.js @args
