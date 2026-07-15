param(
    [Parameter(Mandatory = $true)]
    [string]$JavaPath,

    [Parameter(Mandatory = $true)]
    [string]$WorkingDirectory,

    [string]$QuietArgument = '',

    [Parameter(Mandatory = $true)]
    [string]$LogConfiguration,

    [Parameter(Mandatory = $true)]
    [string]$ResultPath
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)

function Quote-Argument([string]$Value) {
    return '"' + $Value.Replace('"', '\"') + '"'
}

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
    "-Dtrialfinder.output=$ResultPath",
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
$startInfo.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)

$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$firstLine = [Text.StringBuilder]::new()
$firstLineHandled = $false
$buffer = [char[]]::new(4096)

try {
    if (-not $process.Start()) {
        exit 1
    }

    while (($read = $process.StandardOutput.Read($buffer, 0, $buffer.Length)) -gt 0) {
        if ($firstLineHandled) {
            [Console]::Out.Write($buffer, 0, $read)
            continue
        }

        for ($index = 0; $index -lt $read; $index++) {
            $character = $buffer[$index]
            [void]$firstLine.Append($character)
            if ($character -ne "`n") {
                continue
            }

            $line = $firstLine.ToString().TrimEnd("`r", "`n")
            if ($line -ne 'Starting net.fabricmc.loader.impl.game.minecraft.BundlerClassPathCapture') {
                [Console]::Out.Write($firstLine.ToString())
            }
            $firstLineHandled = $true
            if ($index + 1 -lt $read) {
                [Console]::Out.Write($buffer, $index + 1, $read - $index - 1)
            }
            break
        }
    }

    if (-not $firstLineHandled -and $firstLine.Length -gt 0) {
        [Console]::Out.Write($firstLine.ToString())
    }
    $process.WaitForExit()
    exit $process.ExitCode
} finally {
    $process.Dispose()
}
