@echo off
rem Navigate to the directory of this batch file
cd /d "%~dp0"

echo Starting server (headless) via Gradle...
call gradlew.bat headless:run %*

if errorlevel 1 (
    echo:
    echo Application exited with an error. Exit Code: %ERRORLEVEL%
    pause
)
