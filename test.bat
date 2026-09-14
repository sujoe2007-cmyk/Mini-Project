@echo off
setlocal EnableDelayedExpansion

set "JBR_BIN=C:\Program Files\Android\Android Studio\jbr\bin"
if exist "%JBR_BIN%\java.exe" (
    set "JAVA=%JBR_BIN%\java.exe"
) else (
    set "JAVA=java"
)

if not exist "bin\tests\TestRunner.class" (
    echo Binaries not found. Running compilation first...
    call compile.bat
    if !ERRORLEVEL! NEQ 0 (
        echo Compilation failed. Cannot run tests.
        exit /b !ERRORLEVEL!
    )
)

echo.
echo Executing Automated Verification Test Suite...
echo.
"!JAVA!" -cp bin tests.TestRunner
