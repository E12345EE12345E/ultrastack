@echo off
rem Navigate to the directory of this batch file
cd /d "%~dp0"

echo Starting server (headless) via Gradle...
call gradlew.bat headless:run %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo Application exited with an error (Exit Code: %ERRORLEVEL%).
    pause
)
