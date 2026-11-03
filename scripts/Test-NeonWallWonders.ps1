[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
. (Join-Path $PSScriptRoot 'Set-NeonReleaseEntries.ps1')
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$namespace = 'neon-wall-wonders-gwbzt3mk'
$jarName = "$namespace.jar"
$jarPath = Join-Path $repo "artifacts\$jarName"
$oldJarPath = Join-Path $repo "artifacts\$namespace (10).jar"
$expectedHash = '0c9fcc4a8d8468b477f11214efdf8220bfd3e3402b7d56478caeda84efc26f29'

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Read-ZipText($Archive, [string]$Name) {
    $entry = $Archive.GetEntry($Name)
    Require ($null -ne $entry) "Missing jar entry: $Name"
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-NeonIds($Archive) {
    $ids = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($entry in $Archive.Entries) {
        $match = [regex]::Match($entry.FullName,
            "^assets/$namespace/models/item/(neon_[a-z0-9_]+)\.json$")
        if (-not $match.Success) { continue }
        $path = $match.Groups[1].Value
        $id = "${namespace}:$path"
        Require ($ids.Add($id)) "Duplicate item model: $id"
        Require ($null -ne $Archive.GetEntry("assets/$namespace/blockstates/$path.json")) "Missing blockstate: $id"
    }
    return ,$ids
}

Require (Test-Path -LiteralPath $jarPath -PathType Leaf) 'V11 jar is missing.'
Require ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $expectedHash) 'V11 jar checksum does not match the official download.'
$archive = [IO.Compression.ZipFile]::OpenRead($jarPath)
try {
    $manifest = Read-ZipText $archive 'fabric.mod.json' | ConvertFrom-Json
    Require ($manifest.id -ceq $namespace -and $manifest.version -ceq '11.0.0') 'Unexpected Fabric mod ID or version.'
    Require ($manifest.custom.'creativemode:mod'.modVersion -eq 11) 'Unexpected CreativeMode version.'
    Require ($manifest.depends.minecraft -ceq '~1.20.1') 'Unexpected Minecraft requirement.'
    Require ($null -ne $archive.GetEntry("LICENSE_$($namespace.Replace('-', '_'))")) 'Embedded license is missing.'
    $ids = Get-NeonIds $archive
} finally { $archive.Dispose() }
Require ($ids.Count -eq 87) "Expected 87 V11 sign models, found $($ids.Count)."

$oldArchive = [IO.Compression.ZipFile]::OpenRead($oldJarPath)
try { $oldIds = Get-NeonIds $oldArchive } finally { $oldArchive.Dispose() }
Require ($oldIds.Count -eq 65) "Expected 65 V8 sign models, found $($oldIds.Count)."
foreach ($id in $oldIds) { Require ($ids.Contains($id)) "V11 is missing V8 item: $id" }

$tagPath = Join-Path $repo 'config\paxi\datapacks\gangs_kits\data\gangs\tags\items\neon_signs.json'
$tag = Get-Content -LiteralPath $tagPath -Raw | ConvertFrom-Json
Require ($tag.replace -eq $false) 'The neon tag must remain additive.'
$tagIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($id in $tag.values) { Require ($tagIds.Add($id)) "Duplicate neon kit entry: $id" }
Require ($tagIds.Count -eq $ids.Count) 'Neon kit tag does not contain exactly the V11 items.'
foreach ($id in $ids) { Require ($tagIds.Contains($id)) "V11 item missing from neon kits: $id" }

$overridesPath = Join-Path $repo 'config\gangshop\overrides.json'
$overrides = Get-Content -LiteralPath $overridesPath -Raw | ConvertFrom-Json
$v9Only = @(
    'neon_adventurer_backpack', 'neon_campfire', 'neon_community_emblem',
    'neon_enchanted_forest', 'neon_explorer_compass', 'neon_moonlit_mountains',
    'neon_rustic_cabin', 'neon_sunset_trail_marker', 'neon_treasure_chest',
    'neon_wilderness_lantern'
)
foreach ($id in $ids) {
    $denied = $overrides.denyItems.PSObject.Properties[$id]
    Require ($null -ne $denied -and $denied.Value -eq $true) "Neon is not denied in Gang Shop: $id"
}
foreach ($path in $v9Only) {
    $id = "${namespace}:$path"
    Require (-not $ids.Contains($id)) "Recheck the V9 compatibility warning: $id"
    $denied = $overrides.denyItems.PSObject.Properties[$id]
    Require ($null -ne $denied -and $denied.Value -eq $true) "V9 neon is not denied in Gang Shop: $id"
}

$kits = Join-Path $repo 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits'
foreach ($name in @('weekly_1', 'weekly_2', 'weekly_3', 'monthly')) {
    $table = Get-Content -LiteralPath (Join-Path $kits "$name.json") -Raw | ConvertFrom-Json
    $pools = @($table.pools | Where-Object {
        @($_.entries | Where-Object { $_.type -ceq 'minecraft:tag' -and $_.name -ceq 'gangs:neon_signs' -and $_.expand -eq $true }).Count -gt 0
    })
    $rolls = if ($name -eq 'monthly') { 2 } else { 1 }
    Require ($pools.Count -eq 1 -and $pools[0].rolls -eq $rolls) "Neon reward pool changed: $name"
}

$metadataPath = Join-Path $repo 'mods\neon-wall-wonders.pw.toml'
$metadata = [IO.File]::ReadAllText($metadataPath)
Require ($metadata -match '(?m)^filename = "neon-wall-wonders-gwbzt3mk\.jar"\r?$') 'Packwiz filename is not the original V11 filename.'
Require ($metadata -match '(?m)^side = "both"\r?$') 'Neon must be installed on both sides.'
Require ($metadata -match '(?m)^url = "https://raw\.githubusercontent\.com/moelotusdead6-spec/the-gangs-modpack/main/artifacts/neon-wall-wonders-gwbzt3mk\.jar"\r?$') 'Unexpected Packwiz download URL.'
Require ($metadata -match "(?m)^hash = `"$expectedHash`"\r?$") 'Packwiz jar checksum is incorrect.'

$indexPath = Join-Path $repo 'index.toml'
$index = [IO.File]::ReadAllText($indexPath)
foreach ($relative in @('mods/neon-wall-wonders.pw.toml', 'config/gangshop/overrides.json',
        'config/paxi/datapacks/gangs_kits/data/gangs/tags/items/neon_signs.json')) {
    $pattern = '(?m)^file = "' + [regex]::Escape($relative) + '"\r?\nhash = "([0-9a-f]{64})"\r?$'
    $entry = [regex]::Match($index, $pattern)
    Require ($entry.Success) "Packwiz index entry missing: $relative"
    $actual = (Get-FileHash -LiteralPath (Join-Path $repo $relative.Replace('/', '\')) -Algorithm SHA256).Hash.ToLowerInvariant()
    Require ($entry.Groups[1].Value -ceq $actual) "Packwiz index hash mismatch: $relative"
}
$pack = [IO.File]::ReadAllText((Join-Path $repo 'pack.toml'))
$indexHash = (Get-FileHash -LiteralPath $indexPath -Algorithm SHA256).Hash.ToLowerInvariant()
Require ($pack -match "(?m)^hash = `"$indexHash`"\r?$") 'Packwiz pack index hash is incorrect.'

$memory = [IO.MemoryStream]::new()
$fixture = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Update, $true)
try {
    foreach ($name in @(
        'minecraft/mods/neon-wall-wonders-gwbzt3mk (10).jar',
        'minecraft/mods/neon-wall-wonders-gwbzt3mk.jar',
        'client-update/mods/neon-wall-wonders-gwbzt3mk (10).jar',
        'server-update/mods/neon-wall-wonders-gwbzt3mk (9).jar',
        'minecraft/mods/unrelated.jar'
    )) { $null = $fixture.CreateEntry($name) }

    for ($pass = 0; $pass -lt 2; $pass++) {
        Set-NeonReleaseEntries $fixture $jarPath
        $neonEntries = @($fixture.Entries | Where-Object {
            $_.FullName -match '(?i)^(minecraft|client-update|server-update)/mods/neon-wall-wonders-gwbzt3mk(?: \(\d+\))?\.jar$'
        })
        Require ($neonEntries.Count -eq 3) 'Release must contain exactly one neon jar per payload.'
        foreach ($prefix in @('minecraft/', 'client-update/', 'server-update/')) {
            $entry = $fixture.GetEntry($prefix + "mods/$jarName")
            Require ($null -ne $entry) "Release neon jar missing: $prefix"
            $sha256 = [Security.Cryptography.SHA256]::Create()
            $stream = $entry.Open()
            try { $actual = [BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
            finally { $stream.Dispose(); $sha256.Dispose() }
            Require ($actual -ceq $expectedHash) "Release neon jar differs from V11: $prefix"
        }
        Require ($null -ne $fixture.GetEntry('minecraft/mods/unrelated.jar')) 'Release replacement removed an unrelated jar.'
    }
} finally { $fixture.Dispose(); $memory.Dispose() }

Write-Output 'PASS: V11 checksum and 87 models, all V8 IDs retained, kit pools, shop denies, V9-only safeguards, Packwiz hashes, and release replacement.'
