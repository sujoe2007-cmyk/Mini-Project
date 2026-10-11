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

if not exist "bin\com\hospital\HospitalApp.class" (
    echo Binaries not found. Running compilation first...
    call compile.bat
    if !ERRORLEVEL! NEQ 0 (
        echo Compilation failed. Cannot start application.
        pause
        exit /b !ERRORLEVEL!
    )
)

echo Starting Government Hospital Resource Allocation Application...
"!JAVA!" -cp bin com.hospital.HospitalApp %*
pause
