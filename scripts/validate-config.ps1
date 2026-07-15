param(
    [Parameter(Mandatory = $true)]
    [string]$ConfigPath,
    [Parameter(Mandatory = $true)]
    [string]$SelectionPath
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

function Fail([string]$Message) {
    Write-Host "配置错误：$Message" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    Fail '找不到 finder.properties。'
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $ConfigPath -Encoding UTF8) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed.Split('=', 2)
    if ($parts.Count -eq 2) {
        $properties[$parts[0].Trim()] = $parts[1].Trim()
    }
}

$generationVersion = if ($properties.ContainsKey('generation-version')) {
    $properties['generation-version']
} else { '' }
if ([string]::IsNullOrWhiteSpace($generationVersion)) {
    Fail 'generation-version 未填写，请填写 1 或 2。'
}
if ($generationVersion -notin @('1', '2')) {
    Fail "generation-version 只能填写 1 或 2，当前值为 '$generationVersion'。"
}

$seedText = if ($properties.ContainsKey('seed')) { $properties['seed'] } else { '' }
if ([string]::IsNullOrWhiteSpace($seedText)) {
    Fail 'seed 未填写。'
}
if ($seedText -notmatch '^[+-]?\d+$') {
    Fail "seed 必须是十进制整数，当前值为 '$seedText'。"
}
$seed = 0L
if (-not [long]::TryParse(
        $seedText,
        [Globalization.NumberStyles]::AllowLeadingSign,
        [Globalization.CultureInfo]::InvariantCulture,
        [ref]$seed)) {
    Fail 'seed 超出 Java long 范围（-9223372036854775808 至 9223372036854775807）。'
}

if ($generationVersion -eq '1') {
    $engineVersion = '1.21.1'
} else {
    $engineVersion = '26.2'
}

$selectionDir = Split-Path -Parent $SelectionPath
if ($selectionDir) {
    New-Item -ItemType Directory -Force -Path $selectionDir | Out-Null
}
@(
    "set `"GENERATION_VERSION=$generationVersion`""
    "set `"ENGINE_VERSION=$engineVersion`""
    "set `"SEED=$seed`""
) | Set-Content -LiteralPath $SelectionPath -Encoding ASCII
