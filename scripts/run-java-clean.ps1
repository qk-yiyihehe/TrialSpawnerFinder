param(
    [Parameter(Mandatory = $true)]
    [string]$JavaPath,

    [Parameter(Mandatory = $true)]
    [string]$WorkingDirectory,

    [string]$QuietArgument = '',

    [Parameter(Mandatory = $true)]
    [string]$LogConfiguration,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $true)]
    [string]$LauncherLog
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
. (Join-Path $PSScriptRoot 'progress-renderer.ps1')

function Quote-Argument([string]$Value) {
    return '"' + $Value.Replace('"', '\"') + '"'
}

Add-Type -TypeDefinition @'
using System;
using System.Threading;

public static class TrialFinderConsoleCancellation
{
    private static int requested;

    public static bool Requested
    {
        get { return Volatile.Read(ref requested) != 0; }
    }

    public static void Install()
    {
        Volatile.Write(ref requested, 0);
        Console.CancelKeyPress += OnCancel;
    }

    public static void Uninstall()
    {
        Console.CancelKeyPress -= OnCancel;
    }

    private static void OnCancel(object sender, ConsoleCancelEventArgs eventArgs)
    {
        if (Interlocked.Exchange(ref requested, 1) == 0)
        {
            eventArgs.Cancel = true;
            Console.Error.WriteLine();
            Console.Error.WriteLine("Stop requested; waiting for Java to save progress...");
        }
    }
}
'@

$arguments = @()
if ($QuietArgument) {
    $arguments += $QuietArgument
}
$arguments += @(
    '-Xms512M',
    '-Xmx4G',
    '-Dfile.encoding=UTF-8',
    '-Dstdout.encoding=UTF-8',
    '-Dstderr.encoding=UTF-8',
    "-Dlog4j.configurationFile=$LogConfiguration",
    "-Dminecraftfinders.outputDirectory=$OutputDirectory",
    '-jar',
    'fabric-server-launch.jar',
    'nogui'
)

$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $JavaPath
$startInfo.WorkingDirectory = $WorkingDirectory
$startInfo.Arguments = (($arguments | ForEach-Object { Quote-Argument ([string]$_) }) -join ' ')
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
$startInfo.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)

$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$processStarted = $false
$processExitCode = 1
$firstLineHandled = $false
$logDirectory = Split-Path -Parent $LauncherLog
if ($logDirectory -and -not (Test-Path -LiteralPath $logDirectory)) {
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
}
$logWriter = [IO.StreamWriter]::new(
    $LauncherLog, $false, [Text.UTF8Encoding]::new($false))
[TrialFinderConsoleCancellation]::Install()

try {
    $logWriter.WriteLine('Java: ' + $JavaPath)
    $logWriter.WriteLine('Working directory: ' + $WorkingDirectory)
    $logWriter.WriteLine('Started: ' + [DateTimeOffset]::Now.ToString('O'))
    $logWriter.Flush()
    if (-not $process.Start()) {
        exit 1
    }
    $processStarted = $true
    $standardError = $process.StandardError.ReadToEndAsync()

    while (($line = $process.StandardOutput.ReadLine()) -ne $null) {
        $event = ConvertFrom-FinderProgressLine $line
        if ($null -ne $event) {
            Write-FinderProgressEvent $event
            if (Test-FinderProgressLogDue $event) {
                $logWriter.WriteLine('[progress] ' + $event.Line)
                $logWriter.Flush()
            }
            continue
        }
        $logWriter.WriteLine($line)
        $logWriter.Flush()
        if (-not $firstLineHandled -and
                $line -eq 'Starting net.fabricmc.loader.impl.game.minecraft.BundlerClassPathCapture') {
            $firstLineHandled = $true
            continue
        }
        $firstLineHandled = $true
        Write-FinderConsoleLine $line
    }
    $process.WaitForExit()
    $errorText = $standardError.GetAwaiter().GetResult()
    if ($errorText) {
        Close-FinderProgressDisplay
        [Console]::Error.Write($errorText)
        $logWriter.Write($errorText)
    }
    $logWriter.WriteLine()
    $logWriter.WriteLine('Finished: ' + [DateTimeOffset]::Now.ToString('O'))
    $logWriter.WriteLine('Exit code: ' + $process.ExitCode)
    $logWriter.Flush()
    $processExitCode = $process.ExitCode
} finally {
    Close-FinderProgressDisplay
    if ($processStarted -and -not $process.HasExited) {
        if ([TrialFinderConsoleCancellation]::Requested) {
            [void]$process.WaitForExit(15000)
        }
        if (-not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit()
        }
    }
    if ([TrialFinderConsoleCancellation]::Requested) {
        $logWriter.WriteLine('Stopped by Ctrl+C; completed shards remain checkpointed.')
        $logWriter.Flush()
    }
    [TrialFinderConsoleCancellation]::Uninstall()
    $logWriter.Dispose()
    $process.Dispose()
}

if ([TrialFinderConsoleCancellation]::Requested) {
    exit 130
}
exit $processExitCode
