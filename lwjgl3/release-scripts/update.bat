@echo off
cd /d "%~dp0"
echo Close UltraStack before updating.
echo:
"jdk\bin\java.exe" Update.java
if errorlevel 1 (
    echo:
    echo Update failed. Exit Code: %ERRORLEVEL%
)
echo:
pause
