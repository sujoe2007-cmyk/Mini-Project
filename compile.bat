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

:: Clean old compiled class files
if exist "bin" rmdir /s /q bin
mkdir bin

echo Using compiler: !JAVAC!
echo Compiling single source file HospitalApp.java...

"!JAVAC!" -d bin -encoding UTF-8 HospitalApp.java

set COMPILE_STATUS=%ERRORLEVEL%

if %COMPILE_STATUS% EQU 0 (
    echo.
    echo [SUCCESS] Compilation completed successfully!
    echo Compiled class files placed in bin\
) else (
    echo.
    echo [ERROR] Compilation failed with error code %COMPILE_STATUS%.
)

exit /b %COMPILE_STATUS%
