@echo off
setlocal EnableDelayedExpansion

echo ===================================================
echo   Compiling Government Hospital Resource System...
echo ===================================================

set "JBR_BIN=C:\Program Files\Android\Android Studio\jbr\bin"
if exist "%JBR_BIN%\javac.exe" (
    set "JAVAC=%JBR_BIN%\javac.exe"
) else (
    set "JAVAC=javac"
)

echo Using compiler: !JAVAC!
echo Validating and checking compilation of HospitalApp.java...

if not exist "%TEMP%\hospital_build" mkdir "%TEMP%\hospital_build"
"!JAVAC!" -encoding UTF-8 -d "%TEMP%\hospital_build" HospitalApp.java
set COMPILE_STATUS=%ERRORLEVEL%
if exist "%TEMP%\hospital_build" rmdir /s /q "%TEMP%\hospital_build"

if %COMPILE_STATUS% EQU 0 (
    echo.
    echo [SUCCESS] Compilation verified with 0 errors!
    echo Application runs directly from single source: java HospitalApp.java
) else (
    echo.
    echo [ERROR] Compilation failed with error code %COMPILE_STATUS%.
)

exit /b %COMPILE_STATUS%
