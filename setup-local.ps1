param(
    [switch]$CheckOnly,
    [string]$PythonVersion = "3.12",
    [switch]$SkipPythonInstall,
    [switch]$SkipWebInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$WebDir = Join-Path $RepoRoot "web"
$PythonDir = Join-Path $RepoRoot "python\ai_orchestrator"
$PythonVenvDir = Join-Path $PythonDir ".venv"
$PythonExe = Join-Path $PythonVenvDir "Scripts\python.exe"
$PythonRequirements = Join-Path $PythonDir "requirements.txt"
$LocalTempRoot = Join-Path $RepoRoot ".tmp-peai"
$LocalTempDir = Join-Path $LocalTempRoot "temp"
$PipCacheDir = Join-Path $LocalTempRoot "pip-cache"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "[PEAI] $Message"
}

function Test-CommandAvailable {
    param([string]$Name)
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Invoke-LoggedCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory = $RepoRoot
    )

    Write-Host "[RUN] $FilePath $($Arguments -join ' ')"
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
        }
    } finally {
        Pop-Location
    }
}

function Use-RepoTemp {
    if ($CheckOnly) {
        Write-Host "[OK] Would use repo temp directory: $LocalTempRoot"
        return
    }

    New-Item -ItemType Directory -Force -Path $LocalTempDir | Out-Null
    New-Item -ItemType Directory -Force -Path $PipCacheDir | Out-Null
    $env:TEMP = $LocalTempDir
    $env:TMP = $LocalTempDir
    $env:PIP_CACHE_DIR = $PipCacheDir
    Write-Host "[OK] Using repo temp directory: $LocalTempDir"
}

function Ensure-TemplateFile {
    param(
        [string]$Target,
        [string]$Template,
        [string]$Description
    )

    if (Test-Path -LiteralPath $Target) {
        Write-Host "[OK] $Description exists: $Target"
        return
    }

    if (-not (Test-Path -LiteralPath $Template)) {
        Write-Host "[WARN] Template not found, skip ${Description}: $Template"
        return
    }

    if ($CheckOnly) {
        Write-Host "[MISS] $Description missing. Would copy from: $Template"
        return
    }

    Copy-Item -LiteralPath $Template -Destination $Target
    Write-Host "[OK] Created ${Description}: $Target"
}

function Ensure-RequiredTools {
    Write-Step "Checking required system tools"

    if (Test-CommandAvailable "java") {
        Write-Host "[OK] Java found"
    } else {
        throw "Java is not available in PATH. Install Java 17 and open a new terminal."
    }

    if (Test-CommandAvailable "node") {
        Write-Host "[OK] Node.js found"
    } else {
        throw "Node.js is not available in PATH. Install Node.js and open a new terminal."
    }

    if (Test-CommandAvailable "npm") {
        Write-Host "[OK] npm found"
    } else {
        throw "npm is not available in PATH. Install Node.js and open a new terminal."
    }

    if (Test-CommandAvailable "py") {
        Write-Host "[OK] Python launcher found"
    } elseif (Test-CommandAvailable "python") {
        Write-Host "[OK] python command found"
    } else {
        throw "Python is not available. Install Python $PythonVersion and open a new terminal."
    }
}

function Ensure-LocalConfig {
    Write-Step "Checking local config files"

    Ensure-TemplateFile `
        -Target (Join-Path $RepoRoot "local-ports.env") `
        -Template (Join-Path $RepoRoot "local-ports.env.example") `
        -Description "local port config"

    $backendEnv = Join-Path $RepoRoot "backend\.env"
    Ensure-TemplateFile `
        -Target $backendEnv `
        -Template (Join-Path $RepoRoot "backend\.env.example") `
        -Description "backend env config"
}

function Ensure-WebDependencies {
    if ($SkipWebInstall) {
        Write-Step "Skipping web dependency install"
        return
    }

    Write-Step "Checking web dependencies"
    $nodeModules = Join-Path $WebDir "node_modules"
    if (Test-Path -LiteralPath $nodeModules) {
        Write-Host "[OK] web node_modules exists"
        return
    }

    if ($CheckOnly) {
        Write-Host "[MISS] web node_modules missing. Would run: npm install"
        return
    }

    Invoke-LoggedCommand -FilePath "npm" -Arguments @("install") -WorkingDirectory $WebDir
}

function New-PythonVenv {
    if (Test-CommandAvailable "py") {
        Invoke-LoggedCommand -FilePath "py" -Arguments @("-$PythonVersion", "-m", "venv", $PythonVenvDir)
        return
    }
    Invoke-LoggedCommand -FilePath "python" -Arguments @("-m", "venv", $PythonVenvDir)
}

function Test-PythonPip {
    if (-not (Test-Path -LiteralPath $PythonExe)) {
        return $false
    }

    try {
        & $PythonExe -m pip --version *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Ensure-PythonPip {
    if (Test-PythonPip) {
        Write-Host "[OK] Python pip exists"
        return
    }

    if ($CheckOnly) {
        Write-Host "[MISS] Python pip missing. Would run ensurepip in venv."
        return
    }

    Invoke-LoggedCommand -FilePath $PythonExe -Arguments @("-m", "ensurepip", "--upgrade", "--default-pip")
}

function Ensure-PythonDependencies {
    if ($SkipPythonInstall) {
        Write-Step "Skipping Python dependency install"
        return
    }

    Write-Step "Checking Python virtualenv"
    Use-RepoTemp
    if (-not (Test-Path -LiteralPath $PythonExe)) {
        if ($CheckOnly) {
            Write-Host "[MISS] Python virtualenv missing. Would create: $PythonVenvDir"
            return
        }
        New-PythonVenv
    } else {
        Write-Host "[OK] Python virtualenv exists"
    }

    Ensure-PythonPip

    if (-not (Test-Path -LiteralPath $PythonRequirements)) {
        Write-Host "[WARN] Python requirements not found: $PythonRequirements"
        return
    }

    if ($CheckOnly) {
        Write-Host "[OK] Python requirements file exists"
        return
    }

    Invoke-LoggedCommand -FilePath $PythonExe -Arguments @("-m", "pip", "install", "-r", $PythonRequirements)
}

function Test-StartupPrerequisites {
    Write-Step "Running startup prerequisite check"
    Invoke-LoggedCommand -FilePath "cmd" -Arguments @("/c", "start-local.bat", "--check") -WorkingDirectory $RepoRoot
}

Write-Step "Preparing Personal English AI local environment"
if ($CheckOnly) {
    Write-Host "[PEAI] CheckOnly mode: no files will be created and no packages will be installed."
}

Ensure-RequiredTools
Ensure-LocalConfig
Ensure-WebDependencies
Ensure-PythonDependencies

if (-not $CheckOnly) {
    Test-StartupPrerequisites
    Write-Step "Setup complete"
    Write-Host "[PEAI] Start the project with: .\start-local.bat"
} else {
    Write-Step "Check complete"
}
