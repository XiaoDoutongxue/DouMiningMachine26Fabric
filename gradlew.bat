@echo off
setlocal enabledelayedexpansion

rem DouMiningMachine custom Gradle launcher.
rem It uses Gradle 9.5.1 because Fabric Loom 1.17.x requires Gradle 9.5+.

set DIR=%~dp0
set GRADLE_VERSION=9.5.1
set GRADLE_HOME=%DIR%.gradle\local-gradle\gradle-%GRADLE_VERSION%
set GRADLE_ZIP=%DIR%.gradle\local-gradle\gradle-%GRADLE_VERSION%-bin.zip
set GRADLE_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if exist "%GRADLE_HOME%\bin\gradle.bat" goto runGradle

echo [DouMiningMachine] 未检测到 Gradle %GRADLE_VERSION%，正在下载...
if not exist "%DIR%.gradle\local-gradle" mkdir "%DIR%.gradle\local-gradle"

powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%GRADLE_URL%' -OutFile '%GRADLE_ZIP%'"
if errorlevel 1 (
  echo [DouMiningMachine] Gradle 下载失败，请检查网络，或手动安装 Gradle %GRADLE_VERSION%。
  exit /b 1
)

echo [DouMiningMachine] 正在解压 Gradle...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%GRADLE_ZIP%' -DestinationPath '%DIR%.gradle\local-gradle'"
if errorlevel 1 (
  echo [DouMiningMachine] Gradle 解压失败。
  exit /b 1
)

:runGradle
"%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
