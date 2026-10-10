[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineZip,
    [Parameter(Mandatory = $true)]
    [string]$OutputZip
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$baseline = (Resolve-Path -LiteralPath $BaselineZip).Path
$output = [IO.Path]::GetFullPath($OutputZip)
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing release: $output" }
Copy-Item -LiteralPath $baseline -Destination $output
$zip = [IO.Compression.ZipFile]::Open($output, [IO.Compression.ZipArchiveMode]::Update)
$utf8 = [Text.UTF8Encoding]::new($false)
function Write-Entry([string]$Name, [byte[]]$Bytes) {
    $existing = $zip.GetEntry($Name)
    if ($existing) { $existing.Delete() }
    $entry = $zip.CreateEntry($Name, [IO.Compression.CompressionLevel]::Optimal)
    $stream = $entry.Open()
    try { $stream.Write($Bytes, 0, $Bytes.Length) } finally { $stream.Dispose() }
}
function Copy-Entry([string]$Name, [string]$Path) {
    Write-Entry $Name ([IO.File]::ReadAllBytes($Path))
}
function Read-Entry([string]$Name) {
    $entry = $zip.GetEntry($Name)
    if (-not $entry) { throw "Baseline entry missing: $Name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
try {
    foreach ($entry in @($zip.Entries)) {
        if ($entry.FullName -match '(?i)(?:^|/)(?:NaturesCompass-|ExplorersCompass-).*\.jar$' -or
                $entry.FullName -match '^server-update/mods/goldclaim-.*\.jar$') {
            $entry.Delete()
        }
    }
    foreach ($prefix in @('config/', 'minecraft/config/', 'server-update/config/')) {
        $config = Join-Path $repo 'config'
        foreach ($file in Get-ChildItem -LiteralPath $config -Recurse -File) {
            $relative = $file.FullName.Substring($config.Length + 1).Replace('\', '/')
            Copy-Entry ($prefix + $relative) $file.FullName
        }
    }
    Copy-Entry 'README.md' (Join-Path $repo 'README.md')
    Copy-Entry 'server-update/README.txt' (Join-Path $PSScriptRoot 'SERVER-UPDATE.txt')
    Copy-Entry 'server-update/mods/goldclaim-1.0.28.jar' (Join-Path $repo 'artifacts\goldclaim-1.0.28.jar')
    Copy-Entry 'server-update/mods/soulsbackpackscompat-1.0.0.jar' (Join-Path $repo 'artifacts\soulsbackpackscompat-1.0.0.jar')
    Copy-Entry 'minecraft/mods/soulsbackpackscompat-1.0.0.jar' (Join-Path $repo 'artifacts\soulsbackpackscompat-1.0.0.jar')
    foreach ($entry in @($zip.Entries | Where-Object {
            $_.FullName -match '^minecraft/mods/rankbadges-.*\.jar$'
        })) {
        $entry.Delete()
    }
    Copy-Entry 'minecraft/mods/rankbadges-1.0.2.jar' (Join-Path $repo 'artifacts\rankbadges-1.0.2.jar')
    Copy-Entry 'server-update/Install-ServerUpdate.ps1' (Join-Path $PSScriptRoot 'Install-ServerUpdate.ps1')
    Copy-Entry 'server-update/server-settings.properties' (Join-Path $PSScriptRoot 'server-settings.properties')
    foreach ($prefix in @('client-update/', 'server-update/')) {
        Copy-Entry ($prefix + 'Install-ResourcePackDefaults.ps1') (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1')
    }
    Copy-Entry 'client-update/Install-ClientUpdate.ps1' (Join-Path $PSScriptRoot 'Install-ClientUpdate.ps1')
    $instance = Read-Entry 'instance.cfg'
    $instance = [regex]::Replace($instance, '(?m)^name=[^\r\n]*', 'name=The Gangs Modpack v0.1.53')
    Write-Entry 'instance.cfg' ($utf8.GetBytes($instance))
    $options = [IO.File]::ReadAllText((Join-Path $repo 'config\options.txt'))
    $match = [regex]::Match($options, '(?m)^resourcePacks:(.*)\r?$')
    if (-not $match.Success) { throw 'Client options template has no resourcePacks selection.' }
    $selected = @($match.Groups[1].Value.Trim() | ConvertFrom-Json)
    foreach ($entry in $zip.Entries | Where-Object {
            $_.FullName -match '^minecraft/resourcepacks/[^/]+\.zip$'
        } | Sort-Object FullName) {
        $id = 'file/' + [IO.Path]::GetFileName($entry.FullName)
        if ($id -notin $selected) { $selected += $id }
    }
    $line = 'resourcePacks:' + (ConvertTo-Json -InputObject @($selected) -Compress)
    $options = $options.Substring(0, $match.Index) + $line + $options.Substring($match.Index + $match.Length)
    Write-Entry 'minecraft/options.txt' ($utf8.GetBytes($options))
    $record = [ordered]@{ appliedAt = 'release-v0.1.51'; enabledPacks = @($selected) }
    Write-Entry 'minecraft/config/resource-pack-defaults-applied-v1.json' ($utf8.GetBytes((ConvertTo-Json $record)))
} finally { $zip.Dispose() }

$zip = [IO.Compression.ZipFile]::OpenRead($output)
try {
    if (@($zip.Entries | Where-Object { $_.FullName -match '(?i)(NaturesCompass|ExplorersCompass).*\.jar$' }).Count) {
        throw 'Removed compass mod remains in the ZIP.'
    }
    $goldclaim = @($zip.Entries | Where-Object { $_.FullName -match '^server-update/mods/goldclaim-.*\.jar$' })
    if ($goldclaim.Count -ne 1 -or $goldclaim[0].Name -ne 'goldclaim-1.0.28.jar') {
        throw 'Wrong or duplicate GoldClaim server-update jar.'
    }
    foreach ($name in @('server-update/mods/soulsbackpackscompat-1.0.0.jar',
            'minecraft/mods/soulsbackpackscompat-1.0.0.jar')) {
        if (-not $zip.GetEntry($name)) { throw "Required compatibility mod missing: $name" }
    }
    if (@($zip.Entries | Where-Object { $_.FullName -match '^minecraft/mods/goldclaim-' }).Count) {
        throw 'Server-only GoldClaim is present in the client mods folder.'
    }
    $hash = [Security.Cryptography.SHA256]::Create()
    $stream = $goldclaim[0].Open()
    try { $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $hash.Dispose() }
    if ($actual -ne (Get-FileHash (Join-Path $repo 'artifacts\goldclaim-1.0.28.jar') -Algorithm SHA256).Hash) {
        throw 'Packaged GoldClaim does not match the built artifact.'
    }
    foreach ($name in @('minecraft/config/global_packs.toml', 'config/global_packs.toml',
            'server-update/config/global_packs.toml')) {
        $config = Read-Entry $name
        if ($config -notmatch '(?ms)\[resourcepacks\]\s+required = \[\]') {
            throw "Resource packs are still locked: $name"
        }
    }
    foreach ($name in @('minecraft/options.txt', 'minecraft/config/resource-pack-defaults-applied-v1.json',
            'client-update/Install-ClientUpdate.ps1', 'server-update/Install-ServerUpdate.ps1')) {
        if (-not $zip.GetEntry($name)) { throw "Required release entry missing: $name" }
    }
    $options = Read-Entry 'minecraft/options.txt'
    if ($options -notmatch '"file/immersive-interfaces.zip"') { throw 'Immersive Interfaces is not enabled by default.' }
    Write-Output "Verified $($zip.Entries.Count) ZIP entries; both search compass mods absent, GoldClaim and backpack compatibility jars match."
} finally { $zip.Dispose() }
Get-FileHash -LiteralPath $output -Algorithm SHA256
