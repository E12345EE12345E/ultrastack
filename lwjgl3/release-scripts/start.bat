@echo off
rem Debug fallback that launches javaw.exe (no Optimus exports).
rem Windows zips ship start.exe instead; rebuild it with build-start.bat.
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "JAR="
set "COUNT=0"
for %%F in ("UltraStack-*.jar") do (
    set /a COUNT+=1
    set "JAR=%%~fF"
)
if !COUNT! EQU 0 (
    echo ERROR: No UltraStack-*.jar found. Run update.bat first.
    pause
    exit /b 1
)
if !COUNT! GTR 1 (
    echo ERROR: Multiple UltraStack-*.jar files found. Leave only one, or run update.bat.
    pause
    exit /b 1
)

"jdk\bin\javaw.exe" --enable-native-access=ALL-UNNAMED -jar "!JAR!"
if errorlevel 1 (
    echo:
    echo UltraStack failed to start. Exit Code: %ERRORLEVEL%
    pause
)
