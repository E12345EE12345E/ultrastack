@echo off
rem Navigate to the directory of this batch file
cd /d "%~dp0"

rem LAN and dedicated servers both bind NetConfig.PORT (7777). After a client crash
rem the window can close while the JVM is still alive and holding the port.
echo Stopping any server listening on port 7777...

setlocal EnableDelayedExpansion
set "PIDS="

for /f "tokens=5" %%P in ('netstat -ano 2^>nul ^| findstr /R /C:":7777 .*LISTENING"') do (
    if not "%%P"=="0" (
        echo !PIDS! | findstr /C:" %%P " >nul 2>&1
        if errorlevel 1 set "PIDS=!PIDS! %%P "
    )
)

if "!PIDS!"=="" (
    echo No LAN server is currently running.
    exit /b 0
)

set "FAILED=0"
for %%P in (!PIDS!) do (
    echo Killing PID %%P
    taskkill /F /PID %%P
    if errorlevel 1 set "FAILED=1"
)

if !FAILED! neq 0 (
    echo:
    echo Failed to stop one or more processes.
    pause
    exit /b 1
)

echo Done.
