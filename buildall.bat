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
set "CONSTRUO_DIST=lwjgl3\build\construo\dist"
set "WIN_ZIP_NAME=%APP_NAME%-%VERSION%-win.zip"
set "BUNDLE_ZIP_NAME=%APP_NAME%-%VERSION%.zip"
set "BUNDLE_FOLDER=%APP_NAME%-%VERSION%"
set "STAGING=%TEMP%\ultrastack-buildall-%VERSION%"

echo ========================================
echo UltraStack full build ^(%VERSION%^)
echo ========================================
echo.

echo [1/3] Running build.bat (server + signed client jars^)...
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
echo [2/3] Packaging Windows exe via construo (packageWinX64^)...
call gradlew.bat lwjgl3:packageWinX64 %*
if errorlevel 1 (
    echo:
    echo Construo packageWinX64 failed. Exit Code: %ERRORLEVEL%
    pause
    exit /b 1
)

if not exist "%CONSTRUO_DIST%" (
    echo ERROR: Construo output directory not found: %CONSTRUO_DIST%
    pause
    exit /b 1
)

rem Prefer a win-named zip; otherwise take the newest zip in the dist folder.
set "CONSTRUO_ZIP="
for %%F in ("%CONSTRUO_DIST%\*win*.zip") do set "CONSTRUO_ZIP=%%~fF"
if not defined CONSTRUO_ZIP (
    for %%F in ("%CONSTRUO_DIST%\*.zip") do set "CONSTRUO_ZIP=%%~fF"
)
if not defined CONSTRUO_ZIP (
    echo ERROR: No construo zip found in %CONSTRUO_DIST%
    pause
    exit /b 1
)

echo Copying construo package to %LIBS_DIR%\%WIN_ZIP_NAME%
copy /Y "%CONSTRUO_ZIP%" "%LIBS_DIR%\%WIN_ZIP_NAME%" >nul
if errorlevel 1 (
    echo ERROR: Failed to copy construo zip into libs.
    pause
    exit /b 1
)

echo.
echo [3/3] Bundling jar + JDK + start.bat into %BUNDLE_ZIP_NAME%...
if not exist "jdk.zip" (
    echo ERROR: jdk.zip not found in project root.
    pause
    exit /b 1
)

if exist "%STAGING%" rmdir /s /q "%STAGING%"
mkdir "%STAGING%\%BUNDLE_FOLDER%"
if errorlevel 1 (
    echo ERROR: Failed to create staging directory.
    pause
    exit /b 1
)

copy /Y "%LIBS_DIR%\%JAR_NAME%" "%STAGING%\%BUNDLE_FOLDER%\%JAR_NAME%" >nul
if errorlevel 1 (
    echo ERROR: Failed to copy game jar into bundle staging.
    pause
    exit /b 1
)

echo Extracting jdk.zip...
tar -xf "jdk.zip" -C "%STAGING%\%BUNDLE_FOLDER%"
if errorlevel 1 (
    echo ERROR: Failed to extract jdk.zip
    pause
    exit /b 1
)

rem Normalize extracted JDK folder name to "jdk" for a stable start.bat
set "JDK_SRC="
for /d %%D in ("%STAGING%\%BUNDLE_FOLDER%\jdk*") do set "JDK_SRC=%%~fD"
if not defined JDK_SRC (
    echo ERROR: No jdk* folder found after extracting jdk.zip
    pause
    exit /b 1
)
if /I not "%JDK_SRC%"=="%STAGING%\%BUNDLE_FOLDER%\jdk" (
    if exist "%STAGING%\%BUNDLE_FOLDER%\jdk" rmdir /s /q "%STAGING%\%BUNDLE_FOLDER%\jdk"
    move /Y "%JDK_SRC%" "%STAGING%\%BUNDLE_FOLDER%\jdk" >nul
    if errorlevel 1 (
        echo ERROR: Failed to rename extracted JDK folder to jdk
        pause
        exit /b 1
    )
)

(
    echo @echo off
    echo cd /d "%%~dp0"
    echo "jdk\bin\javaw.exe" --enable-native-access=ALL-UNNAMED -jar "%JAR_NAME%"
    echo if errorlevel 1 ^(
    echo     echo:
    echo     echo UltraStack failed to start. Exit Code: %%ERRORLEVEL%%
    echo     pause
    echo ^)
) > "%STAGING%\%BUNDLE_FOLDER%\start.bat"

if exist "%LIBS_DIR%\%BUNDLE_ZIP_NAME%" del /f /q "%LIBS_DIR%\%BUNDLE_ZIP_NAME%"
tar -a -cf "%LIBS_DIR%\%BUNDLE_ZIP_NAME%" -C "%STAGING%" "%BUNDLE_FOLDER%"
if errorlevel 1 (
    echo ERROR: Failed to create %BUNDLE_ZIP_NAME%
    pause
    exit /b 1
)

rmdir /s /q "%STAGING%" 2>nul

echo.
echo ========================================
echo Buildall complete. Outputs in %LIBS_DIR%:
echo   %JAR_NAME%
echo   %WIN_ZIP_NAME%   ^(construo Windows package with .exe^)
echo   %BUNDLE_ZIP_NAME%   ^(jar + bundled JDK + start.bat^)
echo ========================================
endlocal
exit /b 0
