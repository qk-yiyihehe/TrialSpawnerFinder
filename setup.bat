@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "RUNTIME=%~dp0.runtime"
set "SELECTION=%RUNTIME%\selection.bat"
if not exist "%RUNTIME%" mkdir "%RUNTIME%"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\validate-config.ps1" -ConfigPath "%~dp0finder.properties" -SelectionPath "%SELECTION%"
if errorlevel 1 goto failed
call "%SELECTION%"

set "SERVER_DIR=%RUNTIME%\minecraft-%ENGINE_VERSION%"
set "JAVA_DIR=%RUNTIME%\java-25"
set "JDK_ZIP=%RUNTIME%\jdk-25.zip"
set "JAVA_PATH_FILE=%SERVER_DIR%\java-path.txt"
set "RUNTIME_VERSION_FILE=%SERVER_DIR%\runtime-version.txt"
set "ENGINE_JAR=%~dp0engines\trial-spawner-finder-%ENGINE_VERSION%.jar"
set "BOOTSTRAP=%~dp0bootstrap\%ENGINE_VERSION%"
set "DOWNLOAD_SCRIPT=%~dp0scripts\download-with-progress.ps1"
set "JDK_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/github-release/graalvm/graalvm-ce-builds/GraalVM%%20Community%%2025%%20Innovation%%201%%20%%28graal%%2025.1.3%%2C%%20jdk%%2025.0.3%%29/graalvm-community-jdk-25i1-25.0.3_windows-x64_bin.zip"
set "JDK_FALLBACK=https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_windows-x64_bin.zip"
set "LOADER_VERSION=0.19.3"

if "%GENERATION_VERSION%"=="1" (
    set "FABRIC_API_VERSION=0.116.6+1.21.1"
    set "SERVER_SHA1=59353fb40c36d304f2035d51e7d6e6baa98dc05c"
    set "SERVER_SIZE=51627615"
) else (
    set "FABRIC_API_VERSION=0.154.2+26.2"
    set "SERVER_SHA1=823e2250d24b3ddac457a60c92a6a941943fcd6a"
    set "SERVER_SIZE=60894273"
)
set "SERVER_MIRROR=https://bmclapi2.bangbang93.com/v1/objects/%SERVER_SHA1%/server.jar"
set "SERVER_FALLBACK=https://piston-data.mojang.com/v1/objects/%SERVER_SHA1%/server.jar"
set "SERVER_JAR=%SERVER_DIR%\.fabric\server\%ENGINE_VERSION%-server.jar"
set "LEGACY_SERVER_JAR=%SERVER_DIR%\server\%ENGINE_VERSION%-server.jar"
set "BUNDLED_SERVER_JAR=%SERVER_DIR%\versions\%ENGINE_VERSION%\server-%ENGINE_VERSION%.jar"
set "RUNTIME_VERSION=%ENGINE_VERSION%-loader-%LOADER_VERSION%-api-%FABRIC_API_VERSION%-bootstrap-1"

if not exist "%ENGINE_JAR%" goto missing_engine
if not exist "%DOWNLOAD_SCRIPT%" goto missing_download_script
for %%F in (
    "fabric-server-launch.jar"
    ".fabric\server\fabric-loader-server-%LOADER_VERSION%-minecraft-%ENGINE_VERSION%.jar"
    "mods\fabric-api.jar"
    "libraries\net\fabricmc\fabric-loader\%LOADER_VERSION%\fabric-loader-%LOADER_VERSION%.jar"
    "libraries\net\fabricmc\intermediary\%ENGINE_VERSION%\intermediary-%ENGINE_VERSION%.jar"
    "libraries\net\fabricmc\sponge-mixin\0.17.3+mixin.0.8.7\sponge-mixin-0.17.3+mixin.0.8.7.jar"
    "libraries\org\ow2\asm\asm\9.10.1\asm-9.10.1.jar"
    "libraries\org\ow2\asm\asm-analysis\9.10.1\asm-analysis-9.10.1.jar"
    "libraries\org\ow2\asm\asm-commons\9.10.1\asm-commons-9.10.1.jar"
    "libraries\org\ow2\asm\asm-tree\9.10.1\asm-tree-9.10.1.jar"
    "libraries\org\ow2\asm\asm-util\9.10.1\asm-util-9.10.1.jar"
) do if not exist "%BOOTSTRAP%\%%~F" (
    set "MISSING_BOOTSTRAP_FILE=%%~F"
    goto missing_bootstrap
)
if not exist "%SERVER_DIR%" mkdir "%SERVER_DIR%"

set "JAVA_EXE="
set "FALLBACK_JAVA_EXE="
for /f "delims=" %%J in ('dir /b /s /a-d "%~dp0java.exe" 2^>nul ^| findstr.exe /i /v /c:"\.runtime\"') do if not defined JAVA_EXE call :check_graal "%%J"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" if not defined JAVA_EXE call :check_graal "%JAVA_HOME%\bin\java.exe"
for /f "delims=" %%J in ('where.exe java.exe 2^>nul') do if not defined JAVA_EXE call :check_graal "%%J"
if exist "%JAVA_DIR%\bin\java.exe" if not defined JAVA_EXE call :check_graal "%JAVA_DIR%\bin\java.exe"
if defined JAVA_EXE goto java_ready

echo [1/4] 正在下载 GraalVM 25，约 346 MB...
call :download_jdk "%JDK_ZIP%"
if errorlevel 200 goto download_in_use
if errorlevel 1 goto java_download_fallback
if exist "%JAVA_DIR%" rmdir /s /q "%JAVA_DIR%"
mkdir "%JAVA_DIR%"
tar.exe -xf "%JDK_ZIP%" -C "%JAVA_DIR%" --strip-components=1
if errorlevel 1 goto java_extract_fallback
del /q "%JDK_ZIP%"
if not exist "%JAVA_DIR%\bin\java.exe" goto java_extract_fallback
call :check_graal "%JAVA_DIR%\bin\java.exe"
if not defined JAVA_EXE goto java_extract_fallback
goto java_ready

:java_download_fallback
if exist "%JDK_ZIP%" del /q "%JDK_ZIP%"
echo GraalVM 25 下载失败，正在尝试兼容的备用 Java...
goto java_fallback

:java_extract_fallback
if exist "%JDK_ZIP%" del /q "%JDK_ZIP%"
if exist "%JAVA_DIR%" rmdir /s /q "%JAVA_DIR%"
echo GraalVM 25 解压或校验失败，正在尝试兼容的备用 Java...

:java_fallback
for /f "delims=" %%J in ('dir /b /s /a-d "%~dp0java.exe" 2^>nul ^| findstr.exe /i /v /c:"\.runtime\"') do if not defined FALLBACK_JAVA_EXE call :check_compatible "%%J"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" if not defined FALLBACK_JAVA_EXE call :check_compatible "%JAVA_HOME%\bin\java.exe"
for /f "delims=" %%J in ('where.exe java.exe 2^>nul') do if not defined FALLBACK_JAVA_EXE call :check_compatible "%%J"
if exist "%JAVA_DIR%\bin\java.exe" if not defined FALLBACK_JAVA_EXE call :check_compatible "%JAVA_DIR%\bin\java.exe"
if not defined FALLBACK_JAVA_EXE goto java_setup_failed
set "JAVA_EXE=!FALLBACK_JAVA_EXE!"

:java_ready
>"%JAVA_PATH_FILE%" echo !JAVA_EXE!
echo 使用 Java：!JAVA_EXE!
echo [2/4] 正在复制预置 Fabric Loader 与 Fabric API...
xcopy "%BOOTSTRAP%\*" "%SERVER_DIR%\" /E /I /H /Y >nul
if errorlevel 1 goto bootstrap_copy_failed

echo [3/4] 正在安装查找引擎...
copy /y "%ENGINE_JAR%" "%SERVER_DIR%\mods\trial-spawner-finder.jar" >nul
if errorlevel 1 goto bootstrap_copy_failed

echo [4/4] 正在准备 Minecraft %ENGINE_VERSION% 原版服务端（约 50-60 MB）...
if not exist "%SERVER_DIR%\.fabric\server" mkdir "%SERVER_DIR%\.fabric\server"
call :migrate_legacy_server
call :verify_sha1 "%SERVER_JAR%" "%SERVER_SHA1%"
if errorlevel 1 (
    if exist "%SERVER_JAR%" del /q "%SERVER_JAR%"
    echo 优先使用 BMCLAPI 国内镜像；持续低速或超过 90 秒时自动切换 Mojang 官方源。
    call :download_server "%SERVER_JAR%"
    if errorlevel 200 goto download_in_use
    if errorlevel 1 goto download_failed
    call :verify_sha1 "%SERVER_JAR%" "%SERVER_SHA1%"
    if errorlevel 1 goto server_hash_failed
) else (
    echo 原版服务端已经存在且校验通过，跳过下载。
)

if exist "%BUNDLED_SERVER_JAR%" goto fabric_ready
echo 正在本地解包 Minecraft 服务端...
>"%SERVER_DIR%\eula.txt" echo eula=false
>"%SERVER_DIR%\server.properties" echo online-mode=false
>>"%SERVER_DIR%\server.properties" echo level-name=trial-finder-world
pushd "%SERVER_DIR%"
"!JAVA_EXE!" -Xms256M -Xmx1G -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar fabric-server-launch.jar nogui
popd
if not exist "%BUNDLED_SERVER_JAR%" goto fabric_prepare_failed
:fabric_ready
>"%SERVER_DIR%\eula.txt" echo eula=true
>"%RUNTIME_VERSION_FILE%" echo %RUNTIME_VERSION%

echo.
echo Minecraft %ENGINE_VERSION% 运行环境安装完成。
echo 原版服务端与 Fabric 依赖均已准备完成，运行时无需再次下载。
echo 现在可以双击 run.bat。
pause
exit /b 0

:download_jdk
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%DOWNLOAD_SCRIPT%" -Url "!JDK_MIRROR!" -OutputPath "%~1" -Retry 2 -ConnectTimeout 20
if not errorlevel 1 exit /b 0
if errorlevel 200 exit /b 200
if exist "%~1" del /q "%~1"
echo 清华镜像失败，正在尝试 Oracle 官方源...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%DOWNLOAD_SCRIPT%" -Url "!JDK_FALLBACK!" -OutputPath "%~1" -Retry 3 -ConnectTimeout 20
exit /b %ERRORLEVEL%

:download_server
set "SERVER_PART=%~1.part"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%DOWNLOAD_SCRIPT%" -Url "!SERVER_MIRROR!" -OutputPath "!SERVER_PART!" -ExpectedBytes !SERVER_SIZE! -Retry 1 -ConnectTimeout 15 -SpeedLimit 131072 -SpeedTime 15 -MaxTime 90 -Resume
if not errorlevel 1 goto download_server_done
if errorlevel 200 exit /b 200
echo 国内镜像当前过慢或连接失败，正在从 Mojang 官方源断点续传...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%DOWNLOAD_SCRIPT%" -Url "!SERVER_FALLBACK!" -OutputPath "!SERVER_PART!" -ExpectedBytes !SERVER_SIZE! -Retry 3 -ConnectTimeout 20 -SpeedLimit 32768 -SpeedTime 30 -Resume
if errorlevel 200 exit /b 200
if errorlevel 1 (
    echo 官方源不支持当前断点或连接失败，正在从官方源重新下载...
    if exist "!SERVER_PART!" del /q "!SERVER_PART!"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%DOWNLOAD_SCRIPT%" -Url "!SERVER_FALLBACK!" -OutputPath "!SERVER_PART!" -ExpectedBytes !SERVER_SIZE! -Retry 3 -ConnectTimeout 20 -SpeedLimit 32768 -SpeedTime 30
    if errorlevel 200 exit /b 200
    if errorlevel 1 (
        if exist "!SERVER_PART!" del /q "!SERVER_PART!"
        exit /b 1
    )
)
:download_server_done
move /y "!SERVER_PART!" "%~1" >nul
exit /b 0

:verify_sha1
if not exist "%~1" exit /b 1
set "ACTUAL_SHA1="
set "VERIFY_FILE=%~1"
for /f "usebackq delims=" %%H in (`powershell.exe -NoProfile -Command "$stream=[IO.File]::OpenRead($env:VERIFY_FILE); try { [BitConverter]::ToString([Security.Cryptography.SHA1]::Create().ComputeHash($stream)).Replace('-','').ToLowerInvariant() } finally { $stream.Dispose() }"`) do set "ACTUAL_SHA1=%%H"
if /i "!ACTUAL_SHA1!"=="%~2" exit /b 0
exit /b 1

:migrate_legacy_server
call :verify_sha1 "%LEGACY_SERVER_JAR%" "%SERVER_SHA1%"
if errorlevel 1 exit /b 0
echo 检测到旧目录中的完整服务端，正在迁移，无需重新下载。
move /y "%LEGACY_SERVER_JAR%" "%SERVER_JAR%" >nul
exit /b 0

:check_graal
"%~1" -XshowSettings:properties -version >nul 2>"%RUNTIME%\java-version.txt"
findstr.exe /c:"java.version = 25." "%RUNTIME%\java-version.txt" >nul
if errorlevel 1 goto check_graal_done
findstr.exe /c:"GraalVM" "%RUNTIME%\java-version.txt" >nul
if not errorlevel 1 set "JAVA_EXE=%~1"
:check_graal_done
del /q "%RUNTIME%\java-version.txt" >nul 2>&1
exit /b 0

:check_compatible
"%~1" -XshowSettings:properties -version >nul 2>"%RUNTIME%\java-version.txt"
findstr.exe /c:"java.version = 25." "%RUNTIME%\java-version.txt" >nul
if not errorlevel 1 set "FALLBACK_JAVA_EXE=%~1"
if "%GENERATION_VERSION%"=="1" if not defined FALLBACK_JAVA_EXE (
    findstr.exe /c:"java.version = 21." "%RUNTIME%\java-version.txt" >nul
    if not errorlevel 1 set "FALLBACK_JAVA_EXE=%~1"
)
del /q "%RUNTIME%\java-version.txt" >nul 2>&1
exit /b 0

:missing_engine
echo ERROR: 缺少引擎文件：%ENGINE_JAR%
goto failed

:missing_download_script
echo ERROR: 缺少下载进度脚本：%DOWNLOAD_SCRIPT%
goto failed

:missing_bootstrap
echo ERROR: 发行包缺少预置 Fabric 文件：%BOOTSTRAP%\%MISSING_BOOTSTRAP_FILE%
goto failed

:bootstrap_copy_failed
echo ERROR: 无法复制预置 Fabric 文件，请检查目录权限和磁盘空间。
goto failed

:download_failed
echo ERROR: 下载失败，请检查网络后重新运行 setup.bat。
goto failed

:download_in_use
echo ERROR: 下载文件正被其他进程占用，请关闭其他 setup.bat 或 curl.exe 后重新运行。
goto failed

:server_hash_failed
if exist "%SERVER_JAR%" del /q "%SERVER_JAR%"
echo ERROR: 原版服务端 SHA-1 校验失败，已删除不完整文件，请重新运行 setup.bat。
goto failed

:fabric_prepare_failed
echo ERROR: Minecraft 服务端本地解包失败。
goto failed

:java_setup_failed
echo ERROR: GraalVM 25 下载或解压失败，并且没有找到兼容的备用 Java。

:failed
echo.
pause
exit /b 1
