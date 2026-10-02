@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle 8.13 is required. Open this project in Android Studio or install Gradle 8.13, then run this script again.
exit /b 1
