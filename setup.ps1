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

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$minecraftJdk = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta'
$pathJavac = Get-Command javac.exe -ErrorAction SilentlyContinue
$pathJavaHome = if ($pathJavac) {
    Split-Path -Parent (Split-Path -Parent $pathJavac.Source)
} else {
    $null
}
$javaHomes = @(@(
    $env:JDK21_HOME,
    $env:JAVA_HOME,
    $minecraftJdk,
    $pathJavaHome
) | Select-Object -Unique | Where-Object {
    if (-not $_) { return $false }
    $javac = Join-Path $_ 'bin\javac.exe'
    (Test-Path $javac) -and ((& $javac -version 2>&1) -match '^javac 21(?:\.|$)')
})

if (-not $javaHomes) {
    throw 'JDK 21 was not found. Install Java 21 or set JDK21_HOME/JAVA_HOME.'
}

$env:JAVA_HOME = $javaHomes[0]
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
Write-Host "Using JDK: $env:JAVA_HOME"

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
    throw 'GraalVM 25 was not found. Set GRAALVM25_HOME to its installation directory.'
}
Write-Host "Using runtime GraalVM: $runtimeJavaHome"

Push-Location $project
try {
    & .\gradlew.bat clean test :minecraft-1.21.1-runtime:remapJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

    New-Item -ItemType Directory -Force -Path '.runtime' | Out-Null
    Set-Content -LiteralPath '.runtime\build-java-home.txt' -Value $env:JAVA_HOME -Encoding UTF8
    Set-Content -LiteralPath '.runtime\runtime-java-home.txt' -Value $runtimeJavaHome -Encoding UTF8
    New-Item -ItemType Directory -Force -Path 'run' | Out-Null
    Copy-Item 'finder.properties' 'run\finder.properties' -Force
    Write-Host 'Build completed. Run run.bat to start searching.'
} finally {
    Pop-Location
}
