@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean assembleDebug
if errorlevel 1 (
    echo.
    echo Build failed. Open the project in Android Studio and check the Build window.
    pause
    exit /b 1
)
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "Table-Multiplication-debug.apk" >nul
echo.
echo APK ready: %CD%\Table-Multiplication-debug.apk
 pause
endlocal
