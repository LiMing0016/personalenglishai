@echo off
setlocal
call "%~dp0scripts\dev\start-local.bat" %*
exit /b %errorlevel%
