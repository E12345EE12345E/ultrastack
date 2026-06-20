@echo off
rem Navigate to the directory of this batch file
cd /d "%~dp0"

echo Building via Gradle...
call gradlew.bat build %*

if errorlevel 1 (
    echo:
    echo Build failed with an error. Exit Code: %ERRORLEVEL%
    pause
)
