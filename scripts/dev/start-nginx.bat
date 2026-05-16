@echo off
setlocal enabledelayedexpansion

for %%I in ("%~dp0..\..") do set "ROOT=%%~fI\"
set "LOCAL_PORTS_FILE=%ROOT%local-ports.env"
set "LOCAL_PORTS_TEMPLATE=%ROOT%local-ports.env.example"
call :load_config "%LOCAL_PORTS_TEMPLATE%" "template"
call :load_config "%LOCAL_PORTS_FILE%" "local"
call :resolve_nginx_config
if errorlevel 1 exit /b 1

set "NGINX_EXE=%NGINX_DIR%\nginx.exe"
set "PORT=%NGINX_PORT%"

if not exist "%NGINX_EXE%" (
  echo [Nginx] nginx.exe not found: %NGINX_EXE%
  echo [Nginx] Set NGINX_DIR in local-ports.env if your Nginx is in another folder.
  goto :END_FAIL
)

cd /d "%NGINX_DIR%"

echo [Nginx] Testing config...
"%NGINX_EXE%" -t
if errorlevel 1 (
  echo [Nginx] Config test failed. Fix nginx.conf first.
  goto :END_FAIL
)

REM 1) 先看 nginx 进程是否存在（比端口更可靠）
tasklist /fi "imagename eq nginx.exe" | find /i "nginx.exe" >nul
if %errorlevel%==0 (
  echo [Nginx] nginx.exe is running. Reloading...
  "%NGINX_EXE%" -s reload
  if errorlevel 1 (
    echo [Nginx] Reload failed. Check logs\error.log
    goto :END_FAIL
  )
  echo [Nginx] Reload done.
  goto :END_OK
)

REM 2) nginx 不在运行，再检查端口是否被占用（避免启动失败却看不出来）
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT% " ^| findstr LISTENING') do (
  set "PID=%%a"
)

if defined PID (
  echo [Nginx] Port %PORT% is already in use by PID=!PID!. Cannot start nginx.
  echo [Nginx] Use: tasklist /fi "pid eq !PID!"  to identify the process.
  goto :END_FAIL
)

echo [Nginx] Starting...
"%NGINX_EXE%"

REM 3) 启动后再验证：nginx 进程 + 端口
tasklist /fi "imagename eq nginx.exe" | find /i "nginx.exe" >nul
if %errorlevel% neq 0 (
  echo [Nginx] Start failed: nginx.exe process not found. Check logs\error.log
  goto :END_FAIL
)

netstat -ano | findstr ":%PORT% " | findstr LISTENING >nul
if %errorlevel% neq 0 (
  echo [Nginx] Start may have failed: port %PORT% not listening. Check logs\error.log
  goto :END_FAIL
)

echo [Nginx] Started successfully. Port %PORT% is listening.
goto :END_OK

:END_OK
if "%PAUSE_AT_END%"=="1" call :wait_for_enter
exit /b 0

:END_FAIL
if "%PAUSE_AT_END%"=="1" call :wait_for_enter
exit /b 1

:load_config
set "_config_file=%~1"
set "_config_label=%~2"
if exist "%_config_file%" (
  echo [Nginx] Loading %_config_label% port config: %_config_file%
  for /f "usebackq tokens=1,* delims==" %%A in ("%_config_file%") do (
    set "_key=%%A"
    set "_value=%%B"
    if not "!_key!"=="" if not "!_key:~0,1!"=="#" set "!_key!=!_value!"
  )
)
exit /b 0

:resolve_nginx_config
if not defined PORT_OFFSET set "PORT_OFFSET=0"
if not defined NGINX_DIR (
  echo [Nginx] Missing NGINX_DIR. Set it in local-ports.env or local-ports.env.example.
  exit /b 1
)
if not defined NGINX_PORT call :derive_port "NGINX_PORT" "%NGINX_BASE_PORT%" "%PORT_OFFSET%"
if errorlevel 1 exit /b 1
if not defined PAUSE_AT_END set "PAUSE_AT_END=0"
exit /b 0

:derive_port
set "_target=%~1"
set "_base=%~2"
set "_offset=%~3"
if "%_base%"=="" (
  echo [Nginx] Missing base port for %_target%. Set %_target% or %_target:_PORT=_BASE_PORT% in local-ports.env.
  exit /b 1
)
set /a "_derived=%_base% + %_offset%" >nul 2>nul
if errorlevel 1 (
  echo [Nginx] Invalid port expression for %_target%: base=%_base%, offset=%_offset%.
  exit /b 1
)
set "%_target%=%_derived%"
exit /b 0

:wait_for_enter
set /p "_peai_wait=[Nginx] Press Enter to close this window..."
exit /b 0
