Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Script = Join-Path $PSScriptRoot "scripts\dev\setup-local.ps1"
& $Script @args
if ($LASTEXITCODE -is [int]) {
    exit $LASTEXITCODE
}
exit 0
