@echo off
setlocal enabledelayedexpansion

REM ====== 配置区：只改这里 ======
set "NGINX_DIR=D:\nginx-1.28.1"
set "NGINX_PORT=8080"
REM 是否暂停窗口：1=暂停（手动双击用），0=不暂停（IDEA Run 用）
set "PAUSE_AT_END=0"
REM ==============================

set "ROOT=%~dp0"
set "LOCAL_PORTS_FILE=%ROOT%local-ports.env"
call :load_local_config

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
