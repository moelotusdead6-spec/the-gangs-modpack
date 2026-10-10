[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GameDirectory
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$utf8 = [Text.UTF8Encoding]::new($false)
$running = @(Get-CimInstance Win32_Process -Filter "name='java.exe' OR name='javaw.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine.IndexOf($game, [StringComparison]::OrdinalIgnoreCase) -ge 0 })
if ($running.Count) { throw 'A Java process references this server directory. Stop the server before installing.' }
$propertiesPath = Join-Path $game 'server.properties'
$corePath = Join-Path $game 'config\sophisticatedcore-common.toml'
$startPath = Join-Path $game 'start.bat'
foreach ($path in @($propertiesPath, $corePath, (Join-Path $game 'config\global_packs.toml'),
        (Join-Path $PSScriptRoot 'mods\goldclaim-1.0.28.jar'),
        (Join-Path $PSScriptRoot 'mods\soulsbackpackscompat-1.0.0.jar'),
        (Join-Path $PSScriptRoot 'mods\gangscosmetics-2.0.0.jar'),
        (Join-Path $PSScriptRoot 'config\gangscosmetics.json'),
        (Join-Path $PSScriptRoot 'server-settings.properties'),
        (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1'),
        (Join-Path $PSScriptRoot 'config\paxi\datapacks\gangs_kits\data\sophisticatedbackpacks\recipes\inception_upgrade.json'))) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required update/server file is missing: $path" }
}
$core = [IO.File]::ReadAllText($corePath)
$arrays = [regex]::Matches($core, '(?ms)^[ \t]*enabledItems[ \t]*=[ \t]*\[.*?\]')
if ($arrays.Count -ne 1) { throw 'Expected exactly one Sophisticated Core enabledItems array.' }
$array = $arrays[0].Value
$entries = [regex]::Matches($array, '"sophisticatedbackpacks:inception_upgrade\|(true|false)"')
if ($entries.Count -gt 1) { throw 'Duplicate Inception config entries; resolve these before installing.' }
if ($entries.Count -eq 1) {
    $updatedArray = [regex]::Replace($array, '"sophisticatedbackpacks:inception_upgrade\|(true|false)"',
        '"sophisticatedbackpacks:inception_upgrade|false"')
} else {
    $closing = $array.LastIndexOf(']')
    $separator = ''
    if ($array.Substring($array.IndexOf('[') + 1, $closing - $array.IndexOf('[') - 1).Trim().Length) { $separator = ', ' }
    $updatedArray = $array.Substring(0, $closing).TrimEnd() + $separator +
        '"sophisticatedbackpacks:inception_upgrade|false"]'
}
$updatedCore = $core.Substring(0, $arrays[0].Index) + $updatedArray +
    $core.Substring($arrays[0].Index + $arrays[0].Length)
$properties = [IO.File]::ReadAllText($propertiesPath)
foreach ($line in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'server-settings.properties'))) {
    if ($line -notmatch '^(view-distance|simulation-distance|require-resource-pack)=(.+)$') {
        throw "Unsupported server policy setting: $line"
    }
    $pattern = '(?m)^' + [regex]::Escape($Matches[1]) + '[ \t]*=[^\r\n]*'
    if ([regex]::Matches($properties, $pattern).Count -gt 1) { throw "Duplicate server setting: $($Matches[1])" }
    if ([regex]::IsMatch($properties, $pattern)) {
        $properties = [regex]::Replace($properties, $pattern, $line)
    } else {
        $properties = $properties.TrimEnd("`r", "`n") + "`r`n" + $line + "`r`n"
    }
}
$backup = Join-Path $game ('backups\policy-v1.1.0-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
New-Item -ItemType Directory -Path $backup | Out-Null
function Backup-File([string]$Path) {
    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        $relative = $Path.Substring($game.Length).TrimStart('\')
        $destination = Join-Path $backup $relative
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force | Out-Null
        Copy-Item -LiteralPath $Path -Destination $destination
    }
}
foreach ($path in @($propertiesPath, $corePath, $startPath, (Join-Path $game 'config\global_packs.toml'))) {
    Backup-File $path
}
if (Test-Path -LiteralPath $startPath) {
    $start = [IO.File]::ReadAllText($startPath)
    $start = [regex]::Replace($start, 'goldclaim-\d+\.\d+\.\d+\.jar', 'goldclaim-1.0.28.jar')
    [IO.File]::WriteAllText($startPath, $start, $utf8)
}
$mods = Join-Path $game 'mods'
$removed = @(Get-ChildItem -LiteralPath $mods -File | Where-Object {
    $_.Name -match '^(goldclaim-.*\.jar(?:\.pending)?|soulsbackpackscompat-.*\.jar(?:\.pending)?|gangshats-.*\.jar(?:\.pending)?|gangscosmetics-.*\.jar(?:\.pending)?|NaturesCompass-.*\.jar|ExplorersCompass-.*\.jar)$'
})
foreach ($file in $removed) {
    Backup-File $file.FullName
    Remove-Item -LiteralPath $file.FullName
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'mods\goldclaim-1.0.28.jar') -Destination $mods
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'mods\soulsbackpackscompat-1.0.0.jar') -Destination $mods
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'mods\gangscosmetics-2.0.0.jar') -Destination $mods
Backup-File (Join-Path $game 'config\gangscosmetics.json')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'config\gangscosmetics.json') -Destination (Join-Path $game 'config')
[IO.File]::WriteAllText($corePath, $updatedCore, $utf8)
[IO.File]::WriteAllText($propertiesPath, $properties, $utf8)
$relative = 'config\paxi\datapacks\gangs_kits\data\sophisticatedbackpacks\recipes\inception_upgrade.json'
$destination = Join-Path $game $relative
Backup-File $destination
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot $relative) -Destination $destination
& (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1') -Target Server -GameDirectory $game
if ((Get-FileHash (Join-Path $mods 'goldclaim-1.0.28.jar')).Hash -ne
        (Get-FileHash (Join-Path $PSScriptRoot 'mods\goldclaim-1.0.28.jar')).Hash) {
    throw 'Installed GoldClaim hash does not match the supplied update.'
}
if ((Get-FileHash (Join-Path $mods 'soulsbackpackscompat-1.0.0.jar')).Hash -ne
            (Get-FileHash (Join-Path $PSScriptRoot 'mods\soulsbackpackscompat-1.0.0.jar')).Hash) {
        throw 'Installed Soulslike Backpacks Compatibility hash does not match the supplied update.'
}
if ((Get-FileHash (Join-Path $mods 'gangscosmetics-2.0.0.jar')).Hash -ne
            (Get-FileHash (Join-Path $PSScriptRoot 'mods\gangscosmetics-2.0.0.jar')).Hash) {
    throw 'Installed Gangs Cosmetics hash does not match the supplied update.'
}
if (@(Get-ChildItem -LiteralPath $mods -File | Where-Object {
        $_.Name -match '^(NaturesCompass-.*\.jar|ExplorersCompass-.*\.jar|goldclaim-.*\.jar(?:\.pending)?|soulsbackpackscompat-.*\.jar(?:\.pending)?|gangshats-.*\.jar(?:\.pending)?|gangscosmetics-.*\.jar(?:\.pending)?)$' -and
                $_.Name -notin @('goldclaim-1.0.28.jar', 'soulsbackpackscompat-1.0.0.jar', 'gangscosmetics-2.0.0.jar')
        }).Count) { throw 'Old or removed mod jars remain installed.' }
Write-Output "Server policy update installed. Backup: $backup"
Write-Output 'Restart the server now to load the compatibility mod. Worlds, player data, claims, homes, kit data and the RSW journal were not changed. Server remains stopped.'
