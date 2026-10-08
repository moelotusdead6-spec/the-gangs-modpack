[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Client', 'Server')]
    [string]$Target,
    [Parameter(Mandatory = $true)]
    [string]$GameDirectory
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$utf8 = [Text.UTF8Encoding]::new($false)
$configPath = Join-Path $game 'config\global_packs.toml'
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
    throw "Global Packs config not found: $configPath. Select the game/server root, not its config folder."
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-ffff'

function Save-WithBackup([string]$Path, [string]$Content) {
    if (Test-Path -LiteralPath $Path) {
        Copy-Item -LiteralPath $Path -Destination "$Path.resource-pack-backup-$stamp" -ErrorAction Stop
    }
    [IO.File]::WriteAllText($Path, $Content, $utf8)
}

function Get-UnlockedConfig([string]$Content) {
    $sectionPattern = '(?ms)(^\[resourcepacks\][^\r\n]*\r?\n)(.*?)(?=^\[|\z)'
    $section = [regex]::Match($Content, $sectionPattern)
    if (-not $section.Success) {
        throw 'Global Packs config is missing its [resourcepacks] section.'
    }
    $requiredPattern = '(?ms)^[ \t]*required[ \t]*=[ \t]*\[.*?\]'
    $required = [regex]::Matches($section.Groups[2].Value, $requiredPattern)
    if ($required.Count -ne 1) {
        throw 'Expected exactly one resourcepacks.required array; config was not changed.'
    }
    $body = [regex]::Replace($section.Groups[2].Value, $requiredPattern, 'required = []', 1)
    return $Content.Substring(0, $section.Index) + $section.Groups[1].Value + $body +
        $Content.Substring($section.Index + $section.Length)
}

$config = [IO.File]::ReadAllText($configPath)
$unlocked = Get-UnlockedConfig $config

if ($Target -eq 'Server') {
    $propertiesPath = Join-Path $game 'server.properties'
    if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
        throw "Server properties not found: $propertiesPath."
    }
    $properties = [IO.File]::ReadAllText($propertiesPath)
    $updatedProperties = [regex]::Replace($properties,
        '(?m)^require-resource-pack[ \t]*=[^\r\n]*', 'require-resource-pack=false')
    if ($unlocked -ne $config) { Save-WithBackup $configPath $unlocked }
    if ($updatedProperties -ne $properties) { Save-WithBackup $propertiesPath $updatedProperties }
    Write-Output 'Server resource-pack config patched. Restart the server yourself when ready.'
    Write-Output 'Clients must also install the client patch; this does not change client options.'
    return
}

$marker = Join-Path $game 'config\resource-pack-defaults-applied-v1.json'
if (Test-Path -LiteralPath $marker) {
    if ($unlocked -ne $config) { Save-WithBackup $configPath $unlocked }
    Write-Output 'Client defaults were already applied. Saved resource-pack choices were preserved.'
    return
}

$optionsPath = Join-Path $game 'options.txt'
$options = ''
if (Test-Path -LiteralPath $optionsPath) {
    $options = [IO.File]::ReadAllText($optionsPath)
} elseif (Test-Path -LiteralPath (Join-Path $game 'config\options.txt')) {
    $options = [IO.File]::ReadAllText((Join-Path $game 'config\options.txt'))
}
$selection = [regex]::Matches($options, '(?m)^resourcePacks:(.*)\r?$')
if ($selection.Count -gt 1) { throw 'Duplicate resourcePacks entries in options.txt.' }
$selected = @('fabric')
if ($selection.Count -eq 1) {
    $json = $selection[0].Groups[1].Value.Trim()
    if (-not $json.StartsWith('[') -or -not $json.EndsWith(']')) {
        throw 'resourcePacks must be a JSON array; options were not changed.'
    }
    $parsed = ConvertFrom-Json -InputObject $json
    $selected = @($parsed)
    foreach ($id in $selected) {
        if ($id -isnot [string]) { throw 'resourcePacks contains a non-string ID.' }
    }
}

$packsPath = Join-Path $game 'resourcepacks'
$legacyPath = Join-Path $game 'global_packs\required_resources'
$legacyPacks = @()
if (Test-Path -LiteralPath $legacyPath) {
    $legacyPacks = @(Get-ChildItem -LiteralPath $legacyPath | Where-Object {
        ($_.PSIsContainer -and (Test-Path -LiteralPath (Join-Path $_.FullName 'pack.mcmeta'))) -or
        (-not $_.PSIsContainer -and $_.Extension -eq '.zip')
    })
}
# Never overwrite a player's same-named pack with a potentially different legacy pack.
foreach ($pack in $legacyPacks) {
    $destination = Join-Path $packsPath $pack.Name
    if (Test-Path -LiteralPath $destination) {
        throw "Legacy pack name conflicts with $destination. Resolve the duplicate before applying the update."
    }
}
New-Item -ItemType Directory -Path $packsPath -Force | Out-Null
foreach ($pack in $legacyPacks) {
    Copy-Item -LiteralPath $pack.FullName -Destination (Join-Path $packsPath $pack.Name) -Recurse
}

$packs = @(Get-ChildItem -LiteralPath $packsPath | Where-Object {
    ($_.PSIsContainer -and (Test-Path -LiteralPath (Join-Path $_.FullName 'pack.mcmeta'))) -or
    (-not $_.PSIsContainer -and $_.Extension -eq '.zip')
} | Sort-Object Name)
$names = @($packs | ForEach-Object { $_.Name })
# Required Global Packs IDs are bare filenames; vanilla uses file/<filename>.
$selected = @($selected | Where-Object { $_ -notin $names })
foreach ($pack in $packs) {
    $id = 'file/' + $pack.Name
    if ($id -notin $selected) { $selected += $id }
}
$resourceLine = 'resourcePacks:' + (ConvertTo-Json -InputObject @($selected) -Compress)
if ($selection.Count -eq 1) {
    $match = $selection[0]
    $options = $options.Substring(0, $match.Index) + $resourceLine +
        $options.Substring($match.Index + $match.Length)
} else {
    $options = $options.TrimEnd("`r", "`n") + "`r`n" + $resourceLine + "`r`n"
}
if ($unlocked -ne $config) { Save-WithBackup $configPath $unlocked }
Save-WithBackup $optionsPath $options
$record = [ordered]@{ appliedAt = (Get-Date).ToString('o'); enabledPacks = @($selected) }
[IO.File]::WriteAllText($marker, (ConvertTo-Json $record -Depth 3), $utf8)
Write-Output "Client updated: $($packs.Count) installed resource packs enabled and unlocked."
Write-Output 'Launch Minecraft, then use Options > Resource Packs to disable any you do not want.'
Write-Output 'Later launches and installer reruns preserve your choices.'
