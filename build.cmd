@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio1\jbr"
set "ANDROID_HOME=C:\Users\victor\AppData\Local\Android\Sdk"
cd /d "%~dp0"
call gradlew.bat clean assembleRelease bundleRelease --stacktrace > build_out.txt 2>&1
echo DONE=%ERRORLEVEL% >> build_out.txt
