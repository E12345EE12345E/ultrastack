@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Navigate to the directory of this batch file
cd /d "%~dp0"

rem Read projectVersion from gradle.properties (e.g. a.2.0)
set "VERSION="
for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b /c:"projectVersion=" "gradle.properties"`) do (
    set "VERSION=%%B"
)
if not defined VERSION (
    echo ERROR: Could not read projectVersion from gradle.properties
    pause
    exit /b 1
)
rem Trim trailing whitespace
for /f "tokens=* delims= " %%A in ("%VERSION%") do set "VERSION=%%A"

set "APP_NAME=UltraStack"
set "JAR_NAME=%APP_NAME%-%VERSION%.jar"
set "LIBS_DIR=lwjgl3\build\libs"

echo ========================================
echo UltraStack full build ^(%VERSION%^)
echo ========================================
echo.

echo [1/2] Running build.bat (server + signed client jars^)...
call "%~dp0build.bat" %*
if errorlevel 1 (
    echo:
    echo build.bat failed. Aborting buildall.
    pause
    exit /b 1
)

if not exist "%LIBS_DIR%\%JAR_NAME%" (
    echo ERROR: Expected client jar not found: %LIBS_DIR%\%JAR_NAME%
    pause
    exit /b 1
)

echo.
echo [2/2] Packaging platform JDK zips...
call gradlew.bat lwjgl3:packageReleaseZips %*
if errorlevel 1 (
    echo:
    echo packageReleaseZips failed. Exit Code: %ERRORLEVEL%
    pause
    exit /b 1
)

echo.
echo ========================================
echo Buildall complete. Outputs in %LIBS_DIR%:
echo   %JAR_NAME%
echo   %APP_NAME%-%VERSION%-linux-aarch64.zip
echo   %APP_NAME%-%VERSION%-linux-x64.zip
echo   %APP_NAME%-%VERSION%-mac-aarch64.zip
echo   %APP_NAME%-%VERSION%-mac-x64.zip
echo   %APP_NAME%-%VERSION%-windows-x64.zip
echo ========================================
endlocal
exit /b 0
