@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "CARGO="
where cargo >nul 2>nul && set "CARGO=cargo"
if not defined CARGO if exist "%USERPROFILE%\.cargo\bin\cargo.exe" set "CARGO=%USERPROFILE%\.cargo\bin\cargo.exe"
if not defined CARGO (
    echo ERROR: cargo not found. Install Rust from https://rustup.rs
    exit /b 1
)

echo Building start.exe with Rust GNU toolchain...
pushd win-launcher
"!CARGO!" +stable-x86_64-pc-windows-gnu build --release
set "CODE=!ERRORLEVEL!"
popd
if not !CODE! EQU 0 (
    echo ERROR: cargo build failed.
    exit /b 1
)

copy /y "win-launcher\target\release\ultrastack-start.exe" "start.exe" >nul
if errorlevel 1 (
    echo ERROR: could not copy start.exe
    exit /b 1
)

echo Built %cd%\start.exe
exit /b 0
