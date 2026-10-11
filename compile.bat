@echo off
setlocal EnableDelayedExpansion

echo ===================================================
echo   Compiling Government Hospital Resource System...
echo ===================================================

if exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
) else if exist "C:\Users\Thrishiga Sree\Downloads\oracleJdk-27\bin\javac.exe" (
    set "JAVAC=C:\Users\Thrishiga Sree\Downloads\oracleJdk-27\bin\javac.exe"
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe" (
    set "JAVAC=C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
) else (
    set "JAVAC=javac"
)

if not exist "bin" mkdir bin

echo Using compiler: !JAVAC!
echo Compiling source files and test suite...

"!JAVAC!" -cp "lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar" -d bin -encoding UTF-8 src\com\hospital\*.java src\com\hospital\model\*.java src\com\hospital\policy\*.java src\com\hospital\routing\*.java src\com\hospital\service\*.java src\com\hospital\data\*.java tests\*.java

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
