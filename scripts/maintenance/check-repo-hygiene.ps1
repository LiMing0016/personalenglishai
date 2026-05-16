Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Violations = New-Object System.Collections.Generic.List[string]

function Add-Violation {
    param([string]$Message)
    $Violations.Add($Message)
}

function Test-RootPath {
    param([string]$Pattern, [string]$Description)

    $path = Join-Path $RepoRoot $Pattern
    $hasWildcard = $Pattern.IndexOfAny([char[]]"*?[]") -ge 0
    if ($hasWildcard) {
        Get-ChildItem -Force -Path $path -ErrorAction SilentlyContinue |
            ForEach-Object {
                Add-Violation "${Description}: $($_.FullName)"
            }
        return
    }

    if (Test-Path -LiteralPath $path) {
        Add-Violation "${Description}: $path"
    }
}

Test-RootPath "pytest-cache-files-*" "pytest cache directory found at repo root"
Test-RootPath ".pytest_cache" "pytest cache directory found at repo root"
Test-RootPath ".tmp_pip" "pip temp directory found at repo root"
Test-RootPath ".tmp-*" "temporary working directory found at repo root"
Test-RootPath ".tmp_ctx_*.json" "temporary context json found at repo root"
Test-RootPath "*.out.log" "runtime output log found at repo root"
Test-RootPath "*.err.log" "runtime error log found at repo root"

Get-ChildItem -Force -Directory -LiteralPath $RepoRoot -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^tmp[0-9a-zA-Z_]+$' } |
    ForEach-Object { Add-Violation "random tmp directory found at repo root: $($_.FullName)" }

foreach ($path in @("node_modules", "dist")) {
    if (Test-Path -LiteralPath (Join-Path $RepoRoot $path)) {
        Add-Violation "build/dependency directory found at repo root: $path"
    }
}

if ($Violations.Count -gt 0) {
    Write-Host "[repo-hygiene] violations found:" -ForegroundColor Red
    $Violations | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    exit 1
}

Write-Host "[repo-hygiene] ok" -ForegroundColor Green
