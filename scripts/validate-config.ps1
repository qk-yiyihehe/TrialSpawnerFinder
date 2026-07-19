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

$serverPortText = if ($properties.ContainsKey('server-port')) {
    $properties['server-port']
} else { '25566' }
$serverPort = 0
if ((-not [int]::TryParse($serverPortText, [ref]$serverPort)) -or $serverPort -lt 1 -or $serverPort -gt 65535) {
    Fail "server-port 必须是 1 至 65535 的整数，当前值为 '$serverPortText'。"
}

$customJavaHome = if ($properties.ContainsKey('java-home')) {
    [Environment]::ExpandEnvironmentVariables($properties['java-home'].Trim().Trim('"'))
} else { '' }
if ($customJavaHome) {
    $customJava = Join-Path $customJavaHome 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $customJava -PathType Leaf)) {
        Fail "java-home 中找不到 bin\java.exe：$customJavaHome"
    }
}

$searchCenterX = if ($properties.ContainsKey('search-center-x')) {
    $properties['search-center-x']
} else { '0' }
$searchCenterZ = if ($properties.ContainsKey('search-center-z')) {
    $properties['search-center-z']
} else { '0' }
$searchRadius = if ($properties.ContainsKey('search-radius-blocks')) {
    $properties['search-radius-blocks']
} else { '100000' }
$searchAreaShape = if ($properties.ContainsKey('search-area-shape')) {
    $properties['search-area-shape'].ToLowerInvariant()
} else { 'circle' }
$fullWorld = if ($properties.ContainsKey('full-world')) {
    $properties['full-world'].ToLowerInvariant()
} else { 'false' }
$centerXValue = 0
$centerZValue = 0
$radiusValue = 0
if (-not [int]::TryParse($searchCenterX, [ref]$centerXValue)) {
    Fail "search-center-x 必须是整数，当前值为 '$searchCenterX'。"
}
if (-not [int]::TryParse($searchCenterZ, [ref]$centerZValue)) {
    Fail "search-center-z 必须是整数，当前值为 '$searchCenterZ'。"
}
if ((-not [int]::TryParse($searchRadius, [ref]$radiusValue)) -or $radiusValue -le 0) {
    Fail "search-radius-blocks 必须是正整数，当前值为 '$searchRadius'。"
}
if ($searchAreaShape -notin @('circle', 'square')) {
    Fail "search-area-shape 只能是 circle 或 square，当前值为 '$searchAreaShape'。"
}
if ($fullWorld -notin @('true', 'false')) {
    Fail "full-world 只能是 true 或 false，当前值为 '$fullWorld'。"
}
$searchCenterX = $centerXValue
$searchCenterZ = $centerZValue
$searchRadius = $radiusValue

if ($generationVersion -eq '1') {
    $engineVersion = '1.21.1'
} else {
    $engineVersion = '26.2'
}

$selectionDir = Split-Path -Parent $SelectionPath
if ($selectionDir) {
    New-Item -ItemType Directory -Force -Path $selectionDir | Out-Null
}
$customJavaPath = Join-Path $selectionDir 'custom-java-home.txt'
if ($customJavaHome) {
    [IO.File]::WriteAllText(
        $customJavaPath, $customJavaHome, [Text.UTF8Encoding]::new($false))
} elseif (Test-Path -LiteralPath $customJavaPath) {
    Remove-Item -LiteralPath $customJavaPath -Force
}
@(
    "set `"GENERATION_VERSION=$generationVersion`""
    "set `"ENGINE_VERSION=$engineVersion`""
    "set `"SEED=$seed`""
    "set `"SERVER_PORT=$serverPort`""
    "set `"SEARCH_CENTER_X=$searchCenterX`""
    "set `"SEARCH_CENTER_Z=$searchCenterZ`""
    "set `"SEARCH_RADIUS=$searchRadius`""
    "set `"SEARCH_AREA_SHAPE=$searchAreaShape`""
    "set `"FULL_WORLD=$fullWorld`""
) | Set-Content -LiteralPath $SelectionPath -Encoding ASCII
