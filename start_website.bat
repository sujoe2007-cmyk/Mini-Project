@echo off
setlocal EnableDelayedExpansion

echo ====================================================================
echo   Starting Government Hospital Resource Allocation Web Application...
echo ====================================================================

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"

if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else if exist "C:\Users\Thrishiga Sree\Downloads\oracleJdk-27\bin\java.exe" (
    set "JAVA=C:\Users\Thrishiga Sree\Downloads\oracleJdk-27\bin\java.exe"
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
) else (
    set "JAVA=java"
)

if not exist "bin\com\hospital\HospitalApp.class" (
    echo Binaries not found. Running compilation first...
    call compile.bat
    if !ERRORLEVEL! NEQ 0 (
        echo Compilation failed. Cannot start server.
        pause
        exit /b !ERRORLEVEL!
    )
)

echo [OK] Starting Integrated Java Full-Stack Server (REST API + Static Web Server) on port %PORT%...
start "" "http://localhost:%PORT%/"
"!JAVA!" -cp "bin;lib\mysql-connector-j-8.3.0.jar" com.hospital.HospitalApp --server %PORT%
exit /b %ERRORLEVEL%
