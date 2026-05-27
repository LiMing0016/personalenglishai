param(
    [switch]$FailOnBusy
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$LocalPortsTemplate = Join-Path $RepoRoot "local-ports.env.example"
$LocalPortsFile = Join-Path $RepoRoot "local-ports.env"

function Import-PortConfig {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) {
            continue
        }

        $parts = $trimmed.Split("=", 2)
        if ($parts.Count -ne 2) {
            continue
        }

        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        if ($name.Length -gt 0) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

function Get-RequiredIntEnv {
    param(
        [string]$Name,
        [int]$DefaultValue
    )

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }

    $parsed = 0
    if (-not [int]::TryParse($value, [ref]$parsed)) {
        throw "Invalid integer in ${Name}: ${value}"
    }
    return $parsed
}

function Get-PortListeners {
    param([int]$Port)

    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    }

    $rows = netstat -ano | Select-String -Pattern ":$Port\s+.*LISTENING"
    return @($rows | ForEach-Object {
        $columns = ($_.Line -split "\s+") | Where-Object { $_ }
        [pscustomobject]@{
            LocalAddress = $columns[1]
            OwningProcess = [int]$columns[-1]
        }
    })
}

Import-PortConfig -Path $LocalPortsTemplate
Import-PortConfig -Path $LocalPortsFile

$pythonHost = [Environment]::GetEnvironmentVariable("PYTHON_HOST")
if ([string]::IsNullOrWhiteSpace($pythonHost)) {
    $pythonHost = "127.0.0.1"
}

$pythonPortValue = [Environment]::GetEnvironmentVariable("PYTHON_PORT")
if ([string]::IsNullOrWhiteSpace($pythonPortValue)) {
    $basePort = Get-RequiredIntEnv -Name "PYTHON_BASE_PORT" -DefaultValue 8011
    $offset = Get-RequiredIntEnv -Name "PORT_OFFSET" -DefaultValue 0
    $pythonPort = $basePort + $offset
} else {
    $pythonPort = Get-RequiredIntEnv -Name "PYTHON_PORT" -DefaultValue 8011
}

$listeners = Get-PortListeners -Port $pythonPort
if ($listeners.Count -eq 0) {
    Write-Host "[OK] Python orchestrator port is free: http://${pythonHost}:${pythonPort}"
    exit 0
}

Write-Host "[BUSY] Python orchestrator port is already in use: http://${pythonHost}:${pythonPort}"
foreach ($listener in $listeners) {
    $pid = [int]$listener.OwningProcess
    $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        Write-Host "  PID ${pid}: process information unavailable"
        continue
    }

    $path = ""
    try {
        $path = $process.Path
    } catch {
        $path = ""
    }

    if ([string]::IsNullOrWhiteSpace($path)) {
        Write-Host "  PID ${pid}: $($process.ProcessName)"
    } else {
        Write-Host "  PID ${pid}: $($process.ProcessName) - ${path}"
    }
}

Write-Host ""
Write-Host "Fix options:"
Write-Host "  1. Close the old Python/AiOrchestrator run window, then start again."
Write-Host "  2. If it is safe to stop the listed PID, run: Stop-Process -Id <PID>"
Write-Host "  3. Change PYTHON_PORT or PORT_OFFSET in local-ports.env, then update the matching frontend/backend URL settings."

if ($FailOnBusy) {
    exit 1
}

exit 0
