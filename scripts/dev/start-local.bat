@echo off
setlocal enabledelayedexpansion

for %%I in ("%~dp0..\..") do set "ROOT=%%~fI\"
set "BACKEND_DIR=%ROOT%backend"
set "WEB_DIR=%ROOT%web"
set "PYTHON_EXE=%ROOT%python\ai_orchestrator\.venv\Scripts\python.exe"
set "LOCAL_PORTS_FILE=%ROOT%local-ports.env"
set "LOCAL_PORTS_TEMPLATE=%ROOT%local-ports.env.example"

set "CHECK_ONLY=0"
if "%~1"=="--check" set "CHECK_ONLY=1"

call :load_config "%LOCAL_PORTS_TEMPLATE%" "template"
call :load_config "%LOCAL_PORTS_FILE%" "local"
call :resolve_ports
if errorlevel 1 exit /b 1

call :check_prerequisites
if errorlevel 1 exit /b 1

call :check_ports_available
if errorlevel 1 exit /b 1

if "%~1"=="--check" (
  echo [OK] Local startup prerequisites look ready.
  exit /b 0
)

echo [PEAI] Starting backend on http://localhost:%BACKEND_PORT%
start "PEAI Backend %BACKEND_PORT%" powershell -NoExit -ExecutionPolicy Bypass -Command "$env:SERVER_PORT='%BACKEND_PORT%'; $env:APP_BASE_URL='http://localhost:%WEB_PORT%'; $env:APP_DEV_ADMIN_LOGIN_ENABLED='true'; $env:ASSISTANT_ORCHESTRATOR_BASE_URL='http://%PYTHON_HOST%:%PYTHON_PORT%'; $env:VITE_ASSISTANT_API_BASE_URL='http://%PYTHON_HOST%:%PYTHON_PORT%'; $env:AI_CONTEXT_CONVERSATION_PYTHON_BASE_URL='http://%PYTHON_HOST%:%PYTHON_PORT%'; Set-Location -LiteralPath '%BACKEND_DIR%'; .\mvnw.cmd spring-boot:run"

echo [PEAI] Starting web on http://localhost:%WEB_PORT%
start "PEAI Web %WEB_PORT%" powershell -NoExit -ExecutionPolicy Bypass -Command "$env:VITE_API_BASE_URL='http://localhost:%BACKEND_PORT%'; $env:VITE_ASSISTANT_API_BASE_URL='http://%PYTHON_HOST%:%PYTHON_PORT%'; Set-Location -LiteralPath '%WEB_DIR%'; npm run dev -- --host 127.0.0.1 --port %WEB_PORT%"

echo [PEAI] Starting Python orchestrator on http://%PYTHON_HOST%:%PYTHON_PORT%
start "PEAI Python %PYTHON_PORT%" powershell -NoExit -ExecutionPolicy Bypass -Command "Set-Location -LiteralPath '%ROOT%'; & '%PYTHON_EXE%' -m uvicorn python.ai_orchestrator.app:app --host %PYTHON_HOST% --port %PYTHON_PORT% --ws none"

echo.
echo [PEAI] Startup windows opened.
echo [PEAI] Frontend: http://localhost:%WEB_PORT%
echo [PEAI] If backend fails, check backend/.env for datasource and JWT settings.
call :wait_for_enter
exit /b 0

:load_config
set "_config_file=%~1"
set "_config_label=%~2"
if exist "%_config_file%" (
  echo [PEAI] Loading %_config_label% port config: %_config_file%
  for /f "usebackq tokens=1,* delims==" %%A in ("%_config_file%") do (
    set "_key=%%A"
    set "_value=%%B"
    if not "!_key!"=="" if not "!_key:~0,1!"=="#" set "!_key!=!_value!"
  )
)
exit /b 0

:resolve_ports
if not defined PORT_OFFSET set "PORT_OFFSET=0"
if not defined PYTHON_HOST (
  echo [ERROR] Missing PYTHON_HOST. Set it in local-ports.env or local-ports.env.example.
  exit /b 1
)
if not defined BACKEND_PORT call :derive_port "BACKEND_PORT" "%BACKEND_BASE_PORT%" "%PORT_OFFSET%"
if errorlevel 1 exit /b 1
if not defined WEB_PORT call :derive_port "WEB_PORT" "%WEB_BASE_PORT%" "%PORT_OFFSET%"
if errorlevel 1 exit /b 1
if not defined PYTHON_PORT call :derive_port "PYTHON_PORT" "%PYTHON_BASE_PORT%" "%PORT_OFFSET%"
if errorlevel 1 exit /b 1
exit /b 0

:derive_port
set "_target=%~1"
set "_base=%~2"
set "_offset=%~3"
if "%_base%"=="" (
  echo [ERROR] Missing base port for %_target%. Set %_target% or %_target:_PORT=_BASE_PORT% in local-ports.env.
  exit /b 1
)
set /a "_derived=%_base% + %_offset%" >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Invalid port expression for %_target%: base=%_base%, offset=%_offset%.
  exit /b 1
)
set "%_target%=%_derived%"
exit /b 0

:wait_for_enter
set /p "_peai_wait=[PEAI] Press Enter to close this window..."
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

if not exist "%BACKEND_DIR%\.env" (
  echo [WARN] No backend/.env found. Backend may fail until datasource and JWT settings are configured.
)

exit /b 0

:check_ports_available
call :ensure_one_port "BACKEND_PORT" "%BACKEND_PORT%" "Backend"
if errorlevel 1 exit /b 1
call :ensure_one_port "WEB_PORT" "%WEB_PORT%" "Web"
if errorlevel 1 exit /b 1
call :ensure_one_port "PYTHON_PORT" "%PYTHON_PORT%" "Python"
if errorlevel 1 exit /b 1
exit /b 0

:ensure_one_port
set "_var=%~1"
set "_port=%~2"
set "_name=%~3"

:ensure_one_port_loop
call :get_port_pid "%_port%"
if not defined _pid (
  set "%_var%=%_port%"
  exit /b 0
)

echo [WARN] %_name% port %_port% is already in use by PID !_pid!.
if "%CHECK_ONLY%"=="1" (
  echo [ERROR] %_name% port %_port% is occupied. Run start-local.bat without --check to choose Change/Kill/Cancel.
  exit /b 1
)
echo [WARN] Choose an action:
echo [WARN]   C = Change %_name% to a free port and save local-ports.env
echo [WARN]   K = Kill PID !_pid! and keep port %_port%
echo [WARN]   N = Cancel startup
choice /C CKN /N /M "Select C/K/N: "
if errorlevel 3 exit /b 1
if errorlevel 2 (
  call :kill_process "!_pid!"
  if errorlevel 1 (
    echo [ERROR] Failed to kill PID !_pid!. Close it manually or choose another port.
    exit /b 1
  )
  echo [PEAI] Killed PID !_pid!.
  timeout /t 1 /nobreak >nul
  goto ensure_one_port_loop
)
if errorlevel 1 (
  call :find_free_port "%_port%" "_new_port"
  if errorlevel 1 exit /b 1
  set "_port=!_new_port!"
  set "%_var%=!_new_port!"
  call :save_local_port "%_var%" "!_new_port!"
  if errorlevel 1 exit /b 1
  echo [PEAI] %_name% port changed to !_new_port!.
  exit /b 0
)
exit /b 1

:get_port_pid
set "_port=%~1"
set "_pid="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":%_port% " ^| findstr LISTENING') do (
  set "_pid=%%P"
)
exit /b 0

:kill_process
set "_kill_pid=%~1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Stop-Process -Id %_kill_pid% -Force -ErrorAction Stop" >nul 2>nul
if errorlevel 1 exit /b 1
exit /b 0

:find_free_port
set /a "_candidate=%~1 + 100"
:find_free_port_loop
if %_candidate% GTR 65535 (
  echo [ERROR] Could not find a free port after %~1.
  exit /b 1
)
call :get_port_pid "%_candidate%"
if defined _pid (
  set /a "_candidate+=1"
  goto find_free_port_loop
)
set "%~2=%_candidate%"
exit /b 0

:save_local_port
set "_key=%~1"
set "_value=%~2"
if not exist "%LOCAL_PORTS_FILE%" if exist "%ROOT%local-ports.env.example" (
  copy "%ROOT%local-ports.env.example" "%LOCAL_PORTS_FILE%" >nul
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$path='%LOCAL_PORTS_FILE%'; $key='%_key%'; $value='%_value%'; $found=$false; $out=@(); foreach($line in Get-Content -LiteralPath $path){ if($line.StartsWith($key + '=')){ $out += ($key + '=' + $value); $found=$true } else { $out += $line } }; if(-not $found){ $out += ($key + '=' + $value) }; Set-Content -LiteralPath $path -Value $out -Encoding UTF8"
if errorlevel 1 (
  echo [ERROR] Failed to update local-ports.env.
  exit /b 1
)
exit /b 0
