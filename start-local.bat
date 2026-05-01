@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%backend"
set "WEB_DIR=%ROOT%web"
set "PYTHON_EXE=%ROOT%python\ai_orchestrator\.venv\Scripts\python.exe"

call :check_prerequisites
if errorlevel 1 exit /b 1

if "%~1"=="--check" (
  echo [OK] Local startup prerequisites look ready.
  exit /b 0
)

echo [PEAI] Starting backend on http://localhost:18081
start "PEAI Backend 18081" powershell -NoExit -ExecutionPolicy Bypass -Command "$env:SERVER_PORT='18081'; $env:APP_BASE_URL='http://localhost:3300'; $env:AI_CONTEXT_CONVERSATION_PYTHON_BASE_URL='http://127.0.0.1:8011'; Set-Location -LiteralPath '%BACKEND_DIR%'; .\mvnw.cmd spring-boot:run"

echo [PEAI] Starting web on http://localhost:3300
start "PEAI Web 3300" powershell -NoExit -ExecutionPolicy Bypass -Command "Set-Location -LiteralPath '%WEB_DIR%'; npm run dev"

echo [PEAI] Starting Python orchestrator on http://127.0.0.1:8011
start "PEAI Python 8011" powershell -NoExit -ExecutionPolicy Bypass -Command "Set-Location -LiteralPath '%ROOT%'; & '%PYTHON_EXE%' -m uvicorn python.ai_orchestrator.app:app --host 127.0.0.1 --port 8011 --reload"

echo.
echo [PEAI] Startup windows opened.
echo [PEAI] Frontend: http://localhost:3300
echo [PEAI] If backend fails, check backend/.env or .env for datasource and JWT settings.
pause
exit /b 0

:check_prerequisites
if not exist "%BACKEND_DIR%\mvnw.cmd" (
  echo [ERROR] Missing backend Maven wrapper: %BACKEND_DIR%\mvnw.cmd
  exit /b 1
)

if not exist "%WEB_DIR%\package.json" (
  echo [ERROR] Missing web package.json: %WEB_DIR%\package.json
  exit /b 1
)

if not exist "%WEB_DIR%\node_modules\" (
  echo [ERROR] Missing web dependencies. Run: cd web ^&^& npm install
  exit /b 1
)

if not exist "%PYTHON_EXE%" (
  echo [ERROR] Missing Python virtualenv: %PYTHON_EXE%
  echo [ERROR] Create it and install requirements before starting.
  exit /b 1
)

if not exist "%ROOT%.env" if not exist "%BACKEND_DIR%\.env" (
  echo [WARN] No .env or backend/.env found. Backend may fail until datasource and JWT settings are configured.
)

exit /b 0
