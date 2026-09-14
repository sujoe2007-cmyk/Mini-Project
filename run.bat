@echo off
setlocal EnableDelayedExpansion

set "JBR_BIN=C:\Program Files\Android\Android Studio\jbr\bin"
if exist "%JBR_BIN%\java.exe" (
    set "JAVA=%JBR_BIN%\java.exe"
) else (
    set "JAVA=java"
)

if not exist "bin\HospitalApp.class" (
    echo Binaries not found. Running compilation first...
    call compile.bat
    if !ERRORLEVEL! NEQ 0 (
        echo Compilation failed. Cannot start application.
        pause
        exit /b !ERRORLEVEL!
    )
)

echo Starting Government Hospital Resource Allocation Application...
"!JAVA!" -cp bin HospitalApp %*
pause
