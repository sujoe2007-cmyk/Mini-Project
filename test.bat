@echo off
setlocal EnableDelayedExpansion

set "JBR_BIN=C:\Program Files\Android\Android Studio\jbr\bin"
if exist "%JBR_BIN%\java.exe" (
    set "JAVA=%JBR_BIN%\java.exe"
) else (
    set "JAVA=java"
)

echo.
echo Executing Automated Verification Test Suite...
echo.
"!JAVA!" HospitalApp.java --test
