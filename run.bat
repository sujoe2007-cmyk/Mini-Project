@echo off
set JAVA_HOME=C:\Users\Sujoe\.jdks\jbr-21.0.11
set JAVAC=%JAVA_HOME%\bin\javac.exe
set JAVAE=%JAVA_HOME%\bin\java.exe

echo Compiling HospitalApp.java...
"%JAVAC%" HospitalApp.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)
echo [OK] Compiled successfully.
echo.

echo Starting Hospital App...
echo ========================================
"%JAVAE%" HospitalApp
pause
