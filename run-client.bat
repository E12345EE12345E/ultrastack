@echo off
rem Navigate to the directory of this batch file
cd /d "%~dp0"

echo Starting client (LWJGL3) via Gradle...
call gradlew.bat lwjgl3:run %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo Application exited with an error (Exit Code: %ERRORLEVEL%).
    pause
)
