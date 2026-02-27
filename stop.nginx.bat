@echo off
setlocal

set NGINX_DIR=D:\nginx-1.28.1
set NGINX_EXE=%NGINX_DIR%\nginx.exe

cd /d %NGINX_DIR%

echo [Nginx] Stopping...
%NGINX_EXE% -s stop

REM 兜底：如果仍在运行则强杀
tasklist | findstr /i nginx.exe >nul
if %errorlevel%==0 (
  echo [Nginx] Still running, force killing...
  taskkill /F /IM nginx.exe >nul 2>&1
)

echo [Nginx] Stopped.
pause
