Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Script = Join-Path $PSScriptRoot "scripts\dev\check-ai-orchestrator-port.ps1"
& $Script @args
if ($LASTEXITCODE -is [int]) {
    exit $LASTEXITCODE
}
exit 0
