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

if not exist "bin" mkdir bin

echo Using compiler: !JAVAC!
echo Compiling source files...

"!JAVAC!" -d bin -encoding UTF-8 src\com\hospital\*.java src\com\hospital\model\*.java src\com\hospital\policy\*.java src\com\hospital\routing\*.java src\com\hospital\service\*.java src\com\hospital\data\*.java tests\*.java

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
