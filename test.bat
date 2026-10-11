@echo off
setlocal EnableDelayedExpansion

if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else if exist "C:\Users\Thrishiga Sree\Downloads\oracleJdk-27\bin\java.exe" (
    set "JAVA=C:\Users\Thrishiga Sree\Downloads\oracleJdk-27\bin\java.exe"
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
) else (
    set "JAVA=java"
)

if not exist "bin\tests\HospitalAllocationJUnitTest.class" (
    echo Binaries not found. Running compilation first...
    call compile.bat
    if !ERRORLEVEL! NEQ 0 (
        echo Compilation failed. Cannot run tests.
        exit /b !ERRORLEVEL!
    )
)

echo.
echo =====================================================================
echo 1. EXECUTING INTEGRATION TEST RUNNER SCENARIOS (TestRunner)
echo =====================================================================
"!JAVA!" -cp "bin;lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar" tests.TestRunner

echo.
echo =====================================================================
echo 2. EXECUTING PRODUCTION JUNIT 4 VERIFICATION SUITE (Criterion 2)
echo =====================================================================
"!JAVA!" -cp "bin;lib\junit-4.13.2.jar;lib\hamcrest-core-1.3.jar" org.junit.runner.JUnitCore tests.HospitalAllocationJUnitTest

