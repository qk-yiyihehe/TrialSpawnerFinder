$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

function Test-GraalVm25([string]$JavaPath) {
    if (-not (Test-Path -LiteralPath $JavaPath)) { return $false }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $JavaPath
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { return $false }
        $version = $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        return $process.ExitCode -eq 0 -and
            $version -match 'version "25\.' -and $version -match 'GraalVM'
    } finally {
        $process.Dispose()
    }
}

function Test-Java21([string]$JavaPath) {
    if (-not (Test-Path -LiteralPath $JavaPath)) { return $false }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $JavaPath
    $startInfo.Arguments = '-version'
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { return $false }
        $version = $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        return $process.ExitCode -eq 0 -and $version -match 'version "21\.'
    } finally {
        $process.Dispose()
    }
}

$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logPath = Join-Path $project 'launcher.log'
$exitCode = 1
$progressRenderer = Join-Path $project 'scripts\progress-renderer.ps1'

try {
    Start-Transcript -LiteralPath $logPath -Force | Out-Null
    Set-Location $project
    . $progressRenderer

    $runtimeJavaPath = Join-Path $project '.runtime\runtime-java-home.txt'
    if (-not (Test-Path -LiteralPath $runtimeJavaPath)) {
        $graalCandidates = @()
        if ($env:GRAALVM25_HOME) { $graalCandidates += $env:GRAALVM25_HOME }
        $graalCandidates += Get-ChildItem -Path 'D:\*\java\graalvm*' -Directory `
            -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
        $runtimeJavaHome = $graalCandidates |
            Select-Object -Unique |
            Where-Object {
                $java = Join-Path $_ 'bin\java.exe'
                Test-GraalVm25 $java
            } |
            Select-Object -First 1
        if (-not $runtimeJavaHome) {
            throw 'GraalVM 25 was not found. Set GRAALVM25_HOME or run setup.ps1.'
        }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $runtimeJavaPath) |
            Out-Null
        Set-Content -LiteralPath $runtimeJavaPath -Value $runtimeJavaHome -Encoding UTF8
    } else {
        $runtimeJavaHome = (Get-Content -LiteralPath $runtimeJavaPath -Raw -Encoding UTF8).Trim()
    }
    $runtimeJava = Join-Path $runtimeJavaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $runtimeJava)) {
        throw 'The configured runtime GraalVM 25 is no longer available. Run setup.ps1 again.'
    }
    if (-not (Test-GraalVm25 $runtimeJava)) {
        throw 'The configured runtime Java is not GraalVM 25. Set GRAALVM25_HOME and run setup.ps1 again.'
    }
    $buildJavaPath = Join-Path $project '.runtime\build-java-home.txt'
    if (Test-Path -LiteralPath $buildJavaPath) {
        $buildJavaHome = (Get-Content -LiteralPath $buildJavaPath -Raw -Encoding UTF8).Trim()
    } elseif ($env:JDK21_HOME -and (Test-Path (Join-Path $env:JDK21_HOME 'bin\java.exe'))) {
        $buildJavaHome = $env:JDK21_HOME
    } elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $buildJavaHome = $env:JAVA_HOME
    } else {
        $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($null -eq $javaCommand) {
            throw 'Build JDK 21 was not found. Run setup.ps1 first.'
        }
        $buildJavaHome = Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
    }
    $buildJava = Join-Path $buildJavaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $buildJava)) {
        throw 'The configured build JDK 21 is no longer available. Run setup.ps1 again.'
    }
    if (-not (Test-Java21 $buildJava)) {
        throw 'Gradle requires JDK 21. Set JDK21_HOME or run setup.ps1 again.'
    }
    $env:JAVA_HOME = $buildJavaHome
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    $jar = Join-Path $project 'minecraft-1.21.1-runtime\build\libs\minecraft-finders-1.21.1-1.0.0.jar'
    if (-not (Test-Path $jar)) {
        throw 'The project has not been built. Run setup.ps1 first.'
    }

    & (Join-Path $project 'scripts\prepare-run.ps1')
    Write-Host "Starting MinecraftFinders with runtime GraalVM 25: $runtimeJavaHome"
    $savedErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & (Join-Path $project 'gradlew.bat') :minecraft-1.21.1-runtime:runServer `
            "-PruntimeJavaExecutable=$runtimeJava" --console=plain 2>&1 |
            ForEach-Object {
                $line = $_.ToString()
                $event = ConvertFrom-FinderProgressLine $line
                if ($null -ne $event) {
                    Write-FinderProgressEvent $event
                } elseif ($line -ne 'System.Management.Automation.RemoteException') {
                    Write-FinderConsoleLine $line
                }
            }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorActionPreference
    }
    Close-FinderProgressDisplay
    $failureMarker = Join-Path $project 'run\search.failed'
    if (Test-Path -LiteralPath $failureMarker) {
        $detail = Get-Content -LiteralPath $failureMarker -Raw -Encoding UTF8
        throw "MinecraftFinders search failed: $detail"
    }
    if ($exitCode -ne 0) {
        throw "MinecraftFinders exited with code $exitCode."
    }
    Write-Host 'Search completed successfully.'
} catch {
    Write-Host ''
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host ('Full launcher log: ' + $logPath)
    $exitCode = 1
} finally {
    if (Get-Command Close-FinderProgressDisplay -ErrorAction SilentlyContinue) {
        Close-FinderProgressDisplay
    }
    try { Stop-Transcript | Out-Null } catch { }
    Write-Host ''
    if (-not [Console]::IsInputRedirected) {
        Read-Host 'Press Enter to close this window'
    }
}

exit $exitCode
