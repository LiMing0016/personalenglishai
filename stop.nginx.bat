@echo off
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
set "LOCAL_PORTS_FILE=%ROOT%local-ports.env"
set "NGINX_DIR=D:\nginx-1.28.1"

call :load_local_config

set "NGINX_EXE=%NGINX_DIR%\nginx.exe"

if not exist "%NGINX_EXE%" (
  echo [Nginx] nginx.exe not found: %NGINX_EXE%
  echo [Nginx] Set NGINX_DIR in local-ports.env if your Nginx is in another folder.
  call :wait_for_enter
  exit /b 1
)

cd /d "%NGINX_DIR%"

echo [Nginx] Stopping...
"%NGINX_EXE%" -s stop

REM 兜底：如果仍在运行则强杀
tasklist | findstr /i nginx.exe >nul
if %errorlevel%==0 (
  echo [Nginx] Still running, force killing...
  taskkill /F /IM nginx.exe >nul 2>&1
)

echo [Nginx] Stopped.
call :wait_for_enter

exit /b 0

:load_local_config
if exist "%LOCAL_PORTS_FILE%" (
  echo [Nginx] Loading local port config: %LOCAL_PORTS_FILE%
  for /f "usebackq tokens=1,* delims==" %%A in ("%LOCAL_PORTS_FILE%") do (
    set "_key=%%A"
    set "_value=%%B"
    if not "!_key!"=="" if not "!_key:~0,1!"=="#" set "!_key!=!_value!"
  )
)
exit /b 0

:wait_for_enter
set /p "_peai_wait=[Nginx] Press Enter to close this window..."
exit /b 0
