@echo off
setlocal enabledelayedexpansion

REM ====== 配置区：只改这里 ======
set "NGINX_DIR=D:\nginx-1.28.1"
set "NGINX_EXE=%NGINX_DIR%\nginx.exe"
set "PORT=8080"
REM 是否暂停窗口：1=暂停（手动双击用），0=不暂停（IDEA Run 用）
set "PAUSE_AT_END=0"
REM ==============================

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
if "%PAUSE_AT_END%"=="1" pause
exit /b 0

:END_FAIL
if "%PAUSE_AT_END%"=="1" pause
exit /b 1
