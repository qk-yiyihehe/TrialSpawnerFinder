param(
    [Parameter(Mandatory = $true)]
    [string]$Url,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [long]$ExpectedBytes = 0,
    [int]$Retry = 0,
    [int]$ConnectTimeout = 20,
    [int]$SpeedLimit = 0,
    [int]$SpeedTime = 0,
    [int]$MaxTime = 0,
    [switch]$Resume
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)

function Quote-Argument([string]$Value) {
    return '"' + $Value.Replace('"', '\"') + '"'
}

function Test-OutputUnlocked([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return $true
    }
    try {
        $stream = [IO.File]::Open(
            $Path, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $stream.Dispose()
        return $true
    } catch [IO.IOException] {
        return $false
    }
}

function Get-ResponseLength([string]$HeaderPath, [long]$InitialBytes) {
    if (-not (Test-Path -LiteralPath $HeaderPath)) {
        return 0L
    }

    try {
        $headers = [IO.File]::ReadAllText($HeaderPath)
    } catch [IO.IOException] {
        return 0L
    }
    $ranges = [regex]::Matches($headers, '(?im)^content-range:\s*bytes\s+\d+-\d+/(\d+)\s*$')
    if ($ranges.Count -gt 0) {
        return [long]$ranges[$ranges.Count - 1].Groups[1].Value
    }

    $lengths = [regex]::Matches($headers, '(?im)^content-length:\s*(\d+)\s*$')
    if ($lengths.Count -eq 0) {
        return 0L
    }

    $length = [long]$lengths[$lengths.Count - 1].Groups[1].Value
    if ($Resume -and $InitialBytes -gt 0) {
        return $InitialBytes + $length
    }
    return $length
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory -and -not (Test-Path -LiteralPath $outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
if (-not (Test-OutputUnlocked $OutputPath)) {
    [Console]::Error.WriteLine("下载文件正被其他进程占用：$OutputPath")
    [Console]::Error.WriteLine('请关闭其他 setup.bat 或 curl.exe 后重新运行。')
    exit 200
}

$initialBytes = 0L
if ($Resume -and (Test-Path -LiteralPath $OutputPath)) {
    $initialBytes = (Get-Item -LiteralPath $OutputPath).Length
}

$headerPath = "$OutputPath.$PID.$([Guid]::NewGuid().ToString('N')).headers"

$baseCurlArguments = @('-sS', '-fL', '--connect-timeout', $ConnectTimeout)
if ($SpeedLimit -gt 0 -and $SpeedTime -gt 0) {
    $baseCurlArguments += @('--speed-limit', $SpeedLimit, '--speed-time', $SpeedTime)
}
if ($MaxTime -gt 0) {
    $baseCurlArguments += @('--max-time', $MaxTime)
}

$stopwatch = [Diagnostics.Stopwatch]::StartNew()
$lastWidth = 0

function Write-DownloadProgress([bool]$Finished, [bool]$Succeeded) {
    $downloaded = 0L
    if (Test-Path -LiteralPath $OutputPath) {
        $downloaded = (Get-Item -LiteralPath $OutputPath).Length
    }

    $total = $ExpectedBytes
    if ($total -le 0) {
        $total = Get-ResponseLength $headerPath $initialBytes
    }

    $elapsed = [Math]::Max($stopwatch.Elapsed.TotalSeconds, 0.001)
    $newBytes = [Math]::Max(0L, $downloaded - $initialBytes)
    $bytesPerSecond = $newBytes / $elapsed
    if ($bytesPerSecond -ge 1MB) {
        $speed = '{0:N1} MB/s' -f ($bytesPerSecond / 1MB)
    } else {
        $speed = '{0:N0} KB/s' -f ($bytesPerSecond / 1KB)
    }
    if ($total -gt 0) {
        $percent = [Math]::Min(100, $downloaded * 100.0 / $total)
        $line = '下载进度：{0,5:N1}% | {1:N1}/{2:N1} MB | {3}' -f $percent, ($downloaded / 1MB), ($total / 1MB), $speed
    } else {
        $line = '下载进度：{0:N1} MB | {1}' -f ($downloaded / 1MB), $speed
    }

    if ($Finished) {
        $line += if ($Succeeded) { ' | 完成' } else { ' | 未完成' }
    }

    $script:lastWidth = [Math]::Max($script:lastWidth, $line.Length)
    [Console]::Write("`r" + $line.PadRight($script:lastWidth))
    if ($Finished) {
        [Console]::WriteLine()
    }
}

Write-DownloadProgress $false $false
$exitCode = 1
$lastError = ''
try {
    for ($attempt = 0; $attempt -le $Retry; $attempt++) {
        if (Test-Path -LiteralPath $headerPath) {
            Remove-Item -LiteralPath $headerPath -Force
        }

        $curlArguments = @($baseCurlArguments)
        $hasPartialFile = (Test-Path -LiteralPath $OutputPath) -and ((Get-Item -LiteralPath $OutputPath).Length -gt 0)
        if ($Resume -or ($attempt -gt 0 -and $hasPartialFile)) {
            $curlArguments += @('-C', '-')
        }
        $curlArguments += @('-D', $headerPath, '-o', $OutputPath, $Url)

        $startInfo = [Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = 'curl.exe'
        $startInfo.Arguments = (($curlArguments | ForEach-Object { Quote-Argument ([string]$_) }) -join ' ')
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardError = $true

        $process = [Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        $started = $false
        try {
            if (-not $process.Start()) {
                $exitCode = 1
                break
            }
            $started = $true
            $standardError = $process.StandardError.ReadToEndAsync()
            while (-not $process.WaitForExit(500)) {
                Write-DownloadProgress $false $false
            }
            $exitCode = $process.ExitCode
            $lastError = $standardError.GetAwaiter().GetResult().Trim()
        } finally {
            if ($started -and -not $process.HasExited) {
                $process.Kill()
                $process.WaitForExit()
            }
            $process.Dispose()
        }

        if ($exitCode -eq 0 -or $exitCode -eq 33) {
            break
        }
        if ($attempt -lt $Retry) {
            Start-Sleep -Seconds 1
        }
    }

    $stopwatch.Stop()
    Write-DownloadProgress $true ($exitCode -eq 0)
    if ($exitCode -ne 0 -and $lastError) {
        [Console]::Error.WriteLine($lastError)
    }
    exit $exitCode
} finally {
    if (Test-Path -LiteralPath $headerPath) {
        [IO.File]::Delete($headerPath)
    }
}
