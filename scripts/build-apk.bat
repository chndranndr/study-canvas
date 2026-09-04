@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "MODE=%~1"
if not defined MODE set "MODE=debug"
if not "%~2"=="" (
    echo ERROR: only one mode argument is supported.
    goto :usage
)
if /I "%MODE%"=="--check" set "MODE=check"
if /I not "%MODE%"=="debug" if /I not "%MODE%"=="release" if /I not "%MODE%"=="check" goto :usage

for %%I in ("%~dp0..") do set "REPO_ROOT=%%~fI"
set "ANDROID_DIR=%REPO_ROOT%\android"

if not exist "%ANDROID_DIR%\gradlew.bat" (
    echo ERROR: Gradle wrapper not found: "%ANDROID_DIR%\gradlew.bat"
    exit /b 1
)
if not exist "%ANDROID_DIR%\gradle\wrapper\gradle-wrapper.jar" (
    echo ERROR: Gradle wrapper JAR not found: "%ANDROID_DIR%\gradle\wrapper\gradle-wrapper.jar"
    exit /b 1
)

set "ANDROID_SDK="
call :try_sdk "%ANDROID_HOME%"
call :try_sdk "%ANDROID_SDK_ROOT%"
call :try_sdk "%LOCALAPPDATA%\Android\Sdk"
call :try_sdk "%USERPROFILE%\AppData\Local\Android\Sdk"
call :try_sdk "%ProgramFiles%\Android\Sdk"
call :try_sdk "%ProgramFiles(x86)%\Android\Sdk"
call :try_sdk "C:\Android\Sdk"
call :try_sdk "H:\Android\sdk"

if not defined ANDROID_SDK (
    echo ERROR: Android SDK not found.
    echo Set ANDROID_HOME or ANDROID_SDK_ROOT, or install an SDK containing a platforms directory.
    exit /b 1
)

set "ANDROID_HOME=%ANDROID_SDK%"
set "ANDROID_SDK_ROOT=%ANDROID_SDK%"

if /I "%MODE%"=="check" (
    echo Build workflow check passed.
    echo Repository root: "%REPO_ROOT%"
    echo Android SDK: "%ANDROID_SDK%"
    echo Gradle wrapper: "%ANDROID_DIR%\gradlew.bat"
    exit /b 0
)

set "GRADLE_TASK=:app:assembleDebug"
set "APK_PATH=%ANDROID_DIR%\app\build\outputs\apk\debug\app-debug.apk"
if /I "%MODE%"=="release" set "GRADLE_TASK=:app:assembleRelease"
if /I "%MODE%"=="release" set "APK_PATH=%ANDROID_DIR%\app\build\outputs\apk\release\app-release.apk"

pushd "%ANDROID_DIR%" >nul
if errorlevel 1 (
    echo ERROR: unable to enter Android project directory: "%ANDROID_DIR%"
    exit /b 1
)
call "%ANDROID_DIR%\gradlew.bat" --no-daemon --console=plain %GRADLE_TASK%
set "GRADLE_EXIT=%ERRORLEVEL%"
popd

if not "%GRADLE_EXIT%"=="0" (
    echo ERROR: Gradle failed with exit code %GRADLE_EXIT%.
    exit /b %GRADLE_EXIT%
)

if not exist "%APK_PATH%" if /I "%MODE%"=="release" set "APK_PATH=%ANDROID_DIR%\app\build\outputs\apk\release\app-release-unsigned.apk"
if not exist "%APK_PATH%" (
    echo ERROR: Gradle succeeded but the expected APK was not found: "%APK_PATH%"
    exit /b 1
)

for %%I in ("%APK_PATH%") do (
    set "APK_PATH=%%~fI"
    set "APK_NAME=%%~nxI"
    set "APK_SIZE=%%~zI"
)

echo.
echo APK BUILD SUCCEEDED
if /I "%MODE%"=="release" if /I "%APK_NAME%"=="app-release-unsigned.apk" echo WARNING: release APK is unsigned; configure release signing before distribution.
echo APK_PATH: "%APK_PATH%"
echo APK_SIZE_BYTES: %APK_SIZE%
exit /b 0

:try_sdk
if defined ANDROID_SDK exit /b 0
if "%~1"=="" exit /b 0
if exist "%~1\platforms" for %%I in ("%~1") do set "ANDROID_SDK=%%~fI"
exit /b 0

:usage
echo Usage: %~nx0 [debug^|release^|check^|--check]
echo   debug    Build the debug APK (default)
echo   release  Build the release APK
echo   check    Validate SDK and Gradle wrapper discovery without compiling
exit /b 2
