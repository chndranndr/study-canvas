@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ACTION=%~1"
if not defined ACTION set "ACTION=start"
if /I not "%ACTION%"=="start" if /I not "%ACTION%"=="install" if /I not "%ACTION%"=="stop" if /I not "%ACTION%"=="status" goto :usage

for %%I in ("%~dp0..") do set "REPO_ROOT=%%~fI"
set "AVD_NAME=StudyCanvasTablet"
set "SYSTEM_IMAGE=system-images;android-34;google_apis;x86_64"
set "DEVICE_PROFILE=pixel_tablet"

call :find_sdk
if not defined ANDROID_SDK (
    echo ERROR: Android SDK not found.
    echo Set ANDROID_HOME or ANDROID_SDK_ROOT, or install an SDK containing emulator.exe.
    exit /b 1
)

set "EMULATOR=%ANDROID_SDK%\emulator\emulator.exe"
set "ADB=%ANDROID_SDK%\platform-tools\adb.exe"
set "AVDMANAGER=%ANDROID_SDK%\cmdline-tools\latest\bin\avdmanager.bat"
set "IMAGE_DIR=%ANDROID_SDK%\system-images\android-34\google_apis\x86_64"

if not exist "%EMULATOR%" (
    echo ERROR: emulator.exe not found: "%EMULATOR%"
    exit /b 1
)
if not exist "%ADB%" (
    echo ERROR: adb.exe not found: "%ADB%"
    exit /b 1
)
if not exist "%AVDMANAGER%" (
    echo ERROR: avdmanager.bat not found: "%AVDMANAGER%"
    exit /b 1
)

if /I "%ACTION%"=="status" goto :status
if /I "%ACTION%"=="stop" goto :stop

if not exist "%IMAGE_DIR%\package.xml" (
    echo ERROR: required system image is not installed:
    echo        %SYSTEM_IMAGE%
    echo Install it with sdkmanager, then run this script again.
    exit /b 1
)

call :ensure_avd
if errorlevel 1 exit /b 1

call "%ADB%" start-server >nul
call "%ADB%" -e get-state >nul 2>&1
if errorlevel 1 (
    echo Starting %AVD_NAME%...
    start "Study Canvas Tablet" "%EMULATOR%" -avd "%AVD_NAME%" -no-boot-anim -no-snapshot -netdelay none -netspeed full -gpu auto
)

call :wait_for_boot
if errorlevel 1 exit /b 1

if /I "%ACTION%"=="install" (
    set "APK=%REPO_ROOT%\android\app\build\outputs\apk\debug\app-debug.apk"
    if not exist "!APK!" (
        echo ERROR: debug APK not found: "!APK!"
        echo Build it first with scripts\build-apk.bat.
        exit /b 1
    )
    echo Installing debug APK...
    call "%ADB%" -e install -r "!APK!"
    if errorlevel 1 exit /b 1
    call "%ADB%" -e shell monkey -p dev.studycanvas.app 1 >nul
    echo Study Canvas launched on %AVD_NAME%.
)

echo Tablet emulator ready: %AVD_NAME%
exit /b 0

:ensure_avd
set "AVD_FOUND="
for /f "delims=" %%A in ('"%EMULATOR%" -list-avds 2^>nul') do if /I "%%A"=="%AVD_NAME%" set "AVD_FOUND=1"
if defined AVD_FOUND exit /b 0

echo Creating %AVD_NAME% (%DEVICE_PROFILE%, API 34)...
echo no|call "%AVDMANAGER%" create avd --name "%AVD_NAME%" --package "%SYSTEM_IMAGE%" --device "%DEVICE_PROFILE%" --force
if errorlevel 1 (
    echo ERROR: unable to create %AVD_NAME%.
    exit /b 1
)
exit /b 0

:wait_for_boot
echo Waiting for Android to finish booting...
for /l %%N in (1,1,90) do (
    set "BOOT="
    for /f "delims=" %%B in ('"%ADB%" -e shell getprop sys.boot_completed 2^>nul') do set "BOOT=%%B"
    if "!BOOT!"=="1" exit /b 0
    timeout /t 2 /nobreak >nul
)
echo ERROR: emulator did not finish booting within 180 seconds.
exit /b 1

:status
echo Android SDK: %ANDROID_SDK%
echo AVD: %AVD_NAME%
echo.
call "%EMULATOR%" -list-avds
echo.
call "%ADB%" devices
exit /b 0

:stop
call "%ADB%" -e emu kill >nul 2>&1
if errorlevel 1 (
    echo No running Android emulator found.
) else (
    echo %AVD_NAME% stopped.
)
exit /b 0

:find_sdk
set "ANDROID_SDK="
call :try_sdk "%ANDROID_HOME%"
call :try_sdk "%ANDROID_SDK_ROOT%"
call :try_sdk "%LOCALAPPDATA%\Android\Sdk"
call :try_sdk "%USERPROFILE%\AppData\Local\Android\Sdk"
call :try_sdk "C:\Android\Sdk"
call :try_sdk "H:\Android\sdk"
exit /b 0

:try_sdk
if defined ANDROID_SDK exit /b 0
if "%~1"=="" exit /b 0
if exist "%~1\emulator\emulator.exe" for %%I in ("%~1") do set "ANDROID_SDK=%%~fI"
exit /b 0

:usage
echo Usage: %~nx0 [start^|install^|stop^|status]
echo   start    Create and launch the Study Canvas tablet emulator (default)
echo   install  Launch it, install the debug APK, and open Study Canvas
echo   stop     Stop the running emulator
echo   status   Show SDK, AVD, and connected-device status
exit /b 2
