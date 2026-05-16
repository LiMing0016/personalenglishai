@echo off
setlocal
call "%~dp0scripts\dev\stop.nginx.bat" %*
exit /b %errorlevel%
