@echo off
setlocal EnableDelayedExpansion

echo ====================================================================
echo   Starting Government Hospital Resource Allocation Web Application...
echo ====================================================================

where node >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Node.js runtime detected. Starting HTTP Web Server on port 8080...
    node "%~dp0server.js"
    exit /b %ERRORLEVEL%
)

set "JBR_BIN=C:\Program Files\Android\Android Studio\jbr\bin"
if exist "%JBR_BIN%\jwebserver.exe" (
    echo [OK] Java JDK jwebserver detected. Starting web server on port 8080...
    start "" http://localhost:8080/
    "%JBR_BIN%\jwebserver.exe" -d "%~dp0web" -p 8080
    exit /b %ERRORLEVEL%
)

echo [FALLBACK] Starting via direct browser launch...
start "" "%~dp0web\index.html"
echo Dashboard launched in default browser.
pause
