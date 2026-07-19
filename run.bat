@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "RUNTIME=%~dp0.runtime"
set "SELECTION=%RUNTIME%\selection.bat"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\validate-config.ps1" -ConfigPath "%~dp0finder.properties" -SelectionPath "%SELECTION%"
if errorlevel 1 goto failed
call "%SELECTION%"

set "SERVER=%RUNTIME%\minecraft-%ENGINE_VERSION%"
set "JAVA_PATH_FILE=%SERVER%\java-path.txt"
set "ENGINE_JAR=%~dp0engines\trial-spawner-finder-%ENGINE_VERSION%.jar"
set "JAVA_RUNNER=%~dp0scripts\run-java-clean.ps1"
set "LOG_DIR=%~dp0logs"
for /f %%I in ('powershell.exe -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss-fff"') do set "LAUNCHER_LOG=!LOG_DIR!\launcher-%ENGINE_VERSION%-%%I.log"

if not exist "%JAVA_PATH_FILE%" goto not_installed
set /p "JAVA="<"%JAVA_PATH_FILE%"
if not exist "%JAVA%" goto not_installed
set "JAVA_QUIET_ARG="
"%JAVA%" --sun-misc-unsafe-memory-access=allow -version >nul 2>&1
if not errorlevel 1 set "JAVA_QUIET_ARG=--sun-misc-unsafe-memory-access=allow"
if not exist "%SERVER%\fabric-server-launch.jar" goto not_installed
if not exist "%SERVER%\.fabric\server\%ENGINE_VERSION%-server.jar" goto not_installed
if not exist "%SERVER%\.fabric\server\fabric-loader-server-0.19.3-minecraft-%ENGINE_VERSION%.jar" goto not_installed
if not exist "%ENGINE_JAR%" goto missing_engine
if not exist "%JAVA_RUNNER%" goto missing_runner

if exist "%SERVER%\search.failed" del /q "%SERVER%\search.failed"
copy /y "finder.properties" "%SERVER%\finder.properties" >nul
copy /y "%ENGINE_JAR%" "%SERVER%\mods\trial-spawner-finder.jar" >nul
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

>"%SERVER%\eula.txt" echo eula=true
>"%SERVER%\server.properties" echo level-name=trial-finder-runtime-!SEED!
>>"%SERVER%\server.properties" echo level-seed=!SEED!
>>"%SERVER%\server.properties" echo gamemode=spectator
>>"%SERVER%\server.properties" echo generate-structures=true
>>"%SERVER%\server.properties" echo online-mode=false
>>"%SERVER%\server.properties" echo spawn-protection=0
>>"%SERVER%\server.properties" echo view-distance=2
>>"%SERVER%\server.properties" echo simulation-distance=2
>>"%SERVER%\server.properties" echo max-tick-time=-1
>>"%SERVER%\server.properties" echo sync-chunk-writes=false

echo.
echo 正在启动规则版本 %GENERATION_VERSION%，实际引擎 Minecraft %ENGINE_VERSION%...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%JAVA_RUNNER%" -JavaPath "%JAVA%" -WorkingDirectory "%SERVER%" -QuietArgument "!JAVA_QUIET_ARG!" -LogConfiguration "%~dp0scripts\log4j2.xml" -OutputDirectory "%~dp0." -LauncherLog "!LAUNCHER_LOG!"
set "EXIT_CODE=!ERRORLEVEL!"

if "!EXIT_CODE!"=="130" goto stopped

if exist "%SERVER%\search.failed" (
    echo.
    echo 搜索失败，详情：%SERVER%\search.failed
    set "EXIT_CODE=1"
)
if not "!EXIT_CODE!"=="0" (
    echo.
    echo 搜索失败，退出代码：!EXIT_CODE!
    echo 服务端日志：%SERVER%\logs\latest.log
    echo 调试日志：%SERVER%\logs\debug.log
    echo 启动日志：!LAUNCHER_LOG!
)
echo.
pause
exit /b !EXIT_CODE!

:stopped
echo.
echo 搜索已停止。完整分片的断点已经保留，下次使用相同配置运行会自动继续。
echo 启动日志：!LAUNCHER_LOG!
echo.
pause
exit /b 0

:not_installed
echo.
echo ERROR: 尚未安装所选版本 %ENGINE_VERSION% 的运行环境，请先双击 setup.bat。
goto failed

:missing_engine
echo.
echo ERROR: 缺少引擎文件：%ENGINE_JAR%
goto failed

:missing_runner
echo.
echo ERROR: 缺少 Java 输出过滤脚本：%JAVA_RUNNER%

:failed
echo.
pause
exit /b 1
