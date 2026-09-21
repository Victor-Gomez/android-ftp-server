@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio1\jbr"
set "ANDROID_HOME=C:\Users\victor\AppData\Local\Android\Sdk"
cd /d "%~dp0"
call gradlew.bat --no-daemon clean assembleRelease bundleRelease --stacktrace > build_out.txt 2>&1
echo DONE=%ERRORLEVEL% >> build_out.txt
if exist "app\build\outputs\bundle\release\app-release.aab" copy /y "app\build\outputs\bundle\release\app-release.aab" "store_assets\app-release.aab" >nul
if exist "app\build\outputs\apk\release\app-release.apk" copy /y "app\build\outputs\apk\release\app-release.apk" "store_assets\app-release.apk" >nul
