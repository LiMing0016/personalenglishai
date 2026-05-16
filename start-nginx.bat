@echo off
setlocal
call "%~dp0scripts\dev\start-nginx.bat" %*
exit /b %errorlevel%
