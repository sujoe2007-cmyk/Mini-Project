@echo off
setlocal EnableDelayedExpansion

set "JBR_BIN=C:\Program Files\Android\Android Studio\jbr\bin"
if exist "%JBR_BIN%\java.exe" (
    set "JAVA=%JBR_BIN%\java.exe"
) else (
    set "JAVA=java"
)

echo Starting Government Hospital Resource Allocation Application...
"!JAVA!" HospitalApp.java %*
pause
