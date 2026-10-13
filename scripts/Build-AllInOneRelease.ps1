[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineZip,
    [Parameter(Mandatory = $true)]
    [string]$OutputZip,
    [string]$DefaultOptionsJar
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
if (-not $DefaultOptionsJar) {
    $DefaultOptionsJar = Join-Path $repo 'tmp\defaultoptions-fabric-1.20.1-18.0.6.jar'
}
$baseline = (Resolve-Path -LiteralPath $BaselineZip).Path
$output = [IO.Path]::GetFullPath($OutputZip)
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing release: $output" }
Copy-Item -LiteralPath $baseline -Destination $output
$zip = [IO.Compression.ZipFile]::Open($output, [IO.Compression.ZipArchiveMode]::Update)
$utf8 = [Text.UTF8Encoding]::new($false)
. (Join-Path $PSScriptRoot 'Get-PrismAutoUpdateConfig.ps1')
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
    if ((Get-FileHash -LiteralPath $DefaultOptionsJar -Algorithm SHA512).Hash.ToLowerInvariant() -ne
            '43767e00bcc6bf3abf90e2036f7a97b5b9fa37671ddc78b60e28a375e46c7a5e96de6765ec3af1cd556553cad40264ad395881ea98f1b458cd7fbe1a7b92c5c9') {
        throw 'Default Options must match the official Modrinth 18.0.6 Fabric release.'
    }
    foreach ($entry in @($zip.Entries)) {
        if ($entry.FullName -match '(?i)(?:^|/)(?:NaturesCompass-|ExplorersCompass-).*\.jar$' -or
                $entry.FullName -match '^server-update/mods/goldclaim-.*\.jar$' -or
                $entry.FullName -match '^(minecraft|client-update|server-update)/mods/gangshop-.*\.jar$' -or
                $entry.FullName -match '^(minecraft|client-update|server-update)/mods/(soulsbackpackscompat|defaultoptions)-.*\.jar$' -or
                $entry.FullName -match '^(minecraft|client-update|server-update)/mods/jei-.*\.jar$' -or
                $entry.FullName -match '(?i)(?:^|/)YungsBetterEndIsland-.*\.jar$' -or
                $entry.FullName -in @('minecraft/options.txt', 'minecraft/servers.dat',
                    'minecraft/servers.dat_old', 'minecraft/defaultoptions.journal.json',
                    'minecraft/config/resource-pack-defaults-applied-v1.json',
                    'packwiz-prelaunch.jar', 'packwiz-prelaunch.bat', 'packwiz-prelaunch.sh')) {
            $entry.Delete()
        }
    }
    foreach ($prefix in @('config/', 'minecraft/config/', 'server-update/config/')) {
        $config = Join-Path $repo 'config'
        foreach ($file in Get-ChildItem -LiteralPath $config -Recurse -File) {
            $relative = $file.FullName.Substring($config.Length + 1).Replace('\', '/')
            if ($prefix -eq 'server-update/config/' -and $relative.StartsWith('defaultoptions/')) { continue }
            Copy-Entry ($prefix + $relative) $file.FullName
        }
    }
    Copy-Entry 'README.md' (Join-Path $repo 'README.md')
    Copy-Entry 'docs/updates/README.md' (Join-Path $repo 'docs\updates\README.md')
    Copy-Entry 'docs/updates/v1.1.4.md' (Join-Path $repo 'docs\updates\v1.1.4.md')
    Copy-Entry 'docs/updates/v1.1.5.md' (Join-Path $repo 'docs\updates\v1.1.5.md')
    Copy-Entry 'docs/updates/v1.1.6.md' (Join-Path $repo 'docs\updates\v1.1.6.md')
    Copy-Entry 'server-update/README.txt' (Join-Path $PSScriptRoot 'SERVER-UPDATE.txt')
    Copy-Entry 'server-update/mods/goldclaim-1.0.34.jar' (Join-Path $repo 'artifacts\goldclaim-1.0.34.jar')
    Copy-Entry 'server-update/mods/gangshop-1.0.9.jar' (Join-Path $repo 'artifacts\gangshop-1.0.9.jar')
    $newPackMods = @(
        'EndsPhantasm-1.20.1-1.4.jar',
        'Nullscape_1.20.x_v1.2.8.jar',
        'RoughlyEnoughItems-12.1.785-fabric.jar',
        'shield_overhaul-2.0.4.jar',
        'Necronomicon-Fabric-1.6.0+1.20.1.jar',
        'immersive_armors-1.7.2+1.20.1-fabric.jar',
        'bc_particle-0.0.6-fabric-1.20.1.jar',
        'supplementaries-1.20-3.1.44-fabric.jar',
        'suppsquared-1.20-1.1.29.jar',
        'EnderZoology-v8.0.3-1.20.1-Fabric.jar',
        'better-cities-better-end-1.16-1.20.jar',
        'better-end-4.0.11.jar',
        'bclib-3.0.14.jar',
        'mcw-doors-1.1.5-mc1.20.1fabric.jar',
        'mcw-mcwwindows-2.4.2-mc1.20.1fabric.jar',
        'mcw-furniture-3.4.1-mc1.20.1fabric.jar',
        'mcw-roofs-2.3.2-mc1.20.1fabric.jar',
        'mcw-trapdoors-1.1.5-mc1.20.1fabric.jar',
        'elytraslot-fabric-6.4.4+1.20.1.jar'
    )
    foreach ($mod in $newPackMods) {
        Copy-Entry ("minecraft/mods/$mod") (Join-Path $repo "artifacts\$mod")
        Copy-Entry ("server-update/mods/$mod") (Join-Path $repo "artifacts\$mod")
    }
    Copy-Entry 'server-update/mods/soulsbackpackscompat-1.1.0.jar' (Join-Path $repo 'artifacts\soulsbackpackscompat-1.1.0.jar')
    Copy-Entry 'minecraft/mods/soulsbackpackscompat-1.1.0.jar' (Join-Path $repo 'artifacts\soulsbackpackscompat-1.1.0.jar')
    Copy-Entry 'client-update/mods/soulsbackpackscompat-1.1.0.jar' (Join-Path $repo 'artifacts\soulsbackpackscompat-1.1.0.jar')
    foreach ($entry in @($zip.Entries | Where-Object {
            $_.FullName -match '^(minecraft|client-update|server-update)/mods/(gangshats|gangscosmetics)-.*\.jar$'
        })) {
        $entry.Delete()
    }
    Copy-Entry 'minecraft/mods/gangscosmetics-2.0.2.jar' (Join-Path $repo 'artifacts\gangscosmetics-2.0.2.jar')
    Copy-Entry 'server-update/mods/gangscosmetics-2.0.2.jar' (Join-Path $repo 'artifacts\gangscosmetics-2.0.2.jar')
    Copy-Entry 'client-update/mods/gangscosmetics-2.0.2.jar' (Join-Path $repo 'artifacts\gangscosmetics-2.0.2.jar')
    foreach ($entry in @($zip.Entries | Where-Object {
            $_.FullName -match '^minecraft/mods/rankbadges-.*\.jar$'
        })) {
        $entry.Delete()
    }
    Copy-Entry 'minecraft/mods/rankbadges-1.0.2.jar' (Join-Path $repo 'artifacts\rankbadges-1.0.2.jar')
    Copy-Entry 'server-update/Install-ServerUpdate.ps1' (Join-Path $PSScriptRoot 'Install-ServerUpdate.ps1')
    Copy-Entry 'server-update/Prune-ServerBackups.ps1' (Join-Path $PSScriptRoot 'Prune-ServerBackups.ps1')
    Copy-Entry 'server-update/server-settings.properties' (Join-Path $PSScriptRoot 'server-settings.properties')
    foreach ($prefix in @('client-update/', 'server-update/')) {
        Copy-Entry ($prefix + 'Install-ResourcePackDefaults.ps1') (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1')
    }
    Copy-Entry 'client-update/Install-ClientUpdate.ps1' (Join-Path $PSScriptRoot 'Install-ClientUpdate.ps1')
    Copy-Entry 'client-update/Get-PrismAutoUpdateConfig.ps1' (Join-Path $PSScriptRoot 'Get-PrismAutoUpdateConfig.ps1')
    Copy-Entry 'client-update/Repair-PrismAutoUpdate.ps1' (Join-Path $PSScriptRoot 'Repair-PrismAutoUpdate.ps1')
    Copy-Entry 'client-update/README.txt' (Join-Path $PSScriptRoot 'CLIENT-UPDATE.txt')
    foreach ($prefix in @('minecraft/', 'client-update/')) {
        Copy-Entry ($prefix + 'mods/defaultoptions-fabric-1.20.1-18.0.6.jar') $DefaultOptionsJar
        foreach ($name in @('options.txt', 'keybindings.txt', 'servers.dat')) {
            Copy-Entry ($prefix + 'config/defaultoptions/' + $name) (Join-Path $repo ('config\defaultoptions\' + $name))
        }
    }
    $instance = Read-Entry 'instance.cfg'
    $instance = [regex]::Replace($instance, '(?m)^name=[^\r\n]*', 'name=The Gangs Modpack v1.1.6')
    $instance = Get-PrismAutoUpdateConfig $instance
    Write-Entry 'instance.cfg' ($utf8.GetBytes($instance))
    $options = [IO.File]::ReadAllText((Join-Path $repo 'config\defaultoptions\options.txt'))
    $match = [regex]::Match($options, '(?m)^resourcePacks:(.*)\r?$')
    if (-not $match.Success) { throw 'Client options template has no resourcePacks selection.' }
    $parsed = ConvertFrom-Json -InputObject $match.Groups[1].Value.Trim()
    $selected = @($parsed)
    foreach ($id in $selected) {
        if ($id -isnot [string]) { throw 'Resource-pack defaults must be strings.' }
    }
    foreach ($entry in $zip.Entries | Where-Object {
            $_.FullName -match '^minecraft/resourcepacks/[^/]+\.zip$'
        } | Sort-Object FullName) {
        $id = 'file/' + [IO.Path]::GetFileName($entry.FullName)
        if ($id -notin $selected) {
            throw "Missing resource-pack default $id. Run Build-ClientDefaults.ps1 against this baseline first."
        }
    }
} finally { $zip.Dispose() }

$zip = [IO.Compression.ZipFile]::OpenRead($output)
try {
    if (@($zip.Entries | Where-Object { $_.FullName -match '(?i)(NaturesCompass|ExplorersCompass|jei-|YungsBetterEndIsland-).*\.jar$' }).Count) {
        throw 'Removed mod remains in the ZIP.'
    }
    $goldclaim = @($zip.Entries | Where-Object { $_.FullName -match '^server-update/mods/goldclaim-.*\.jar$' })
    if ($goldclaim.Count -ne 1 -or $goldclaim[0].Name -ne 'goldclaim-1.0.34.jar') {
        throw 'Wrong or duplicate GoldClaim server-update jar.'
    }
    foreach ($name in @('server-update/mods/soulsbackpackscompat-1.1.0.jar',
            'minecraft/mods/soulsbackpackscompat-1.1.0.jar',
            'client-update/mods/soulsbackpackscompat-1.1.0.jar')) {
        $entry = $zip.GetEntry($name)
        if (-not $entry) { throw "Required compatibility mod missing: $name" }
        $hash = [Security.Cryptography.SHA256]::Create()
        $stream = $entry.Open()
        try { $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '') }
        finally { $stream.Dispose(); $hash.Dispose() }
        if ($actual -ne (Get-FileHash (Join-Path $repo 'artifacts\soulsbackpackscompat-1.1.0.jar') -Algorithm SHA256).Hash) {
            throw "Packaged compatibility mod does not match the built artifact: $name"
        }
    }
    if (@($zip.Entries | Where-Object {
            $_.FullName -match '^(minecraft|client-update|server-update)/mods/soulsbackpackscompat-.*\.jar$'
        }).Count -ne 3) { throw 'Duplicate or outdated compatibility mod remains in the ZIP.' }
    foreach ($name in @('server-update/mods/gangscosmetics-2.0.2.jar',
            'minecraft/mods/gangscosmetics-2.0.2.jar',
            'client-update/mods/gangscosmetics-2.0.2.jar')) {
        $entry = $zip.GetEntry($name)
        if (-not $entry) { throw "Required cosmetics mod missing: $name" }
        $hash = [Security.Cryptography.SHA256]::Create()
        $stream = $entry.Open()
        try { $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '') }
        finally { $stream.Dispose(); $hash.Dispose() }
        if ($actual -ne (Get-FileHash (Join-Path $repo 'artifacts\gangscosmetics-2.0.2.jar') -Algorithm SHA256).Hash) {
            throw "Packaged Gangs Cosmetics does not match the built artifact: $name"
        }
    }
    if (@($zip.Entries | Where-Object {
            $_.FullName -match '^(minecraft|client-update|server-update)/mods/gangshats-.*\.jar$'
        }).Count) { throw 'Legacy Gangs Hats remains in the ZIP.' }
    if (@($zip.Entries | Where-Object { $_.FullName -match '^minecraft/mods/goldclaim-' }).Count) {
        throw 'Server-only GoldClaim is present in the client mods folder.'
    }
    if (@($zip.Entries | Where-Object { $_.FullName -match '^(minecraft|client-update)/mods/gangshop-' }).Count) {
        throw 'Server-only Gang Shop is present in a client payload.'
    }
    $shop = @($zip.Entries | Where-Object { $_.FullName -match '^server-update/mods/gangshop-.*\.jar$' })
    if ($shop.Count -ne 1 -or $shop[0].Name -ne 'gangshop-1.0.9.jar') {
        throw 'Wrong or duplicate Gang Shop server jar.'
    }
    $hash = [Security.Cryptography.SHA256]::Create()
    $stream = $shop[0].Open()
    try { $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $hash.Dispose() }
    if ($actual -ne (Get-FileHash (Join-Path $repo 'artifacts\gangshop-1.0.9.jar')).Hash) {
        throw 'Packaged Gang Shop does not match the built artifact.'
    }
    if (-not $zip.GetEntry('minecraft/mods/RoughlyEnoughItems-12.1.785-fabric.jar')) {
        throw 'Roughly Enough Items is missing from client mods.'
    }
    foreach ($name in @('minecraft/mods/tru.e-ending-v1.1.0c.jar',
            'minecraft/mods/GoodEnding-1.20.1-1.1.0-Fabric.jar',
            'server-update/config/paxi/datapacks/gangs_fixes/data/true_ending/functions/tick.mcfunction')) {
        if (-not $zip.GetEntry($name)) { throw "Retained End overhaul or RSW compatibility missing: $name" }
    }
    $hash = [Security.Cryptography.SHA256]::Create()
    $stream = $goldclaim[0].Open()
    try { $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '') }
    finally { $stream.Dispose(); $hash.Dispose() }
    if ($actual -ne (Get-FileHash (Join-Path $repo 'artifacts\goldclaim-1.0.34.jar') -Algorithm SHA256).Hash) {
        throw 'Packaged GoldClaim does not match the built artifact.'
    }
    foreach ($mod in $newPackMods) {
        $metadata = @(Get-ChildItem -LiteralPath (Join-Path $repo 'mods') -Filter '*.pw.toml' |
            Where-Object { [IO.File]::ReadAllText($_.FullName).Contains('filename = "' + $mod + '"') })
        if ($metadata.Count -ne 1) { throw "Expected exactly one Packwiz record for $mod." }
        $text = [IO.File]::ReadAllText($metadata[0].FullName)
        if ($text -notmatch '(?m)^side = "both"$') { throw "Wrong client/server side for $mod." }
        $algorithm = [regex]::Match($text, '(?m)^hash-format = "([^"]+)"').Groups[1].Value
        $expected = [regex]::Match($text, '(?m)^hash = "([^"]+)"').Groups[1].Value
        foreach ($prefix in @('minecraft/mods/', 'server-update/mods/')) {
            $entry = $zip.GetEntry($prefix + $mod)
            if (-not $entry) { throw "New mod missing: $prefix$mod" }
            $hash = [Security.Cryptography.HashAlgorithm]::Create($algorithm)
            $stream = $entry.Open()
            try { $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
            finally { $stream.Dispose(); $hash.Dispose() }
            if ($actual -ne $expected) { throw "Packwiz/ZIP hash mismatch for $prefix$mod." }
        }
    }
    foreach ($name in @('minecraft/config/global_packs.toml', 'config/global_packs.toml',
            'server-update/config/global_packs.toml')) {
        $config = Read-Entry $name
        if ($config -notmatch '(?ms)\[resourcepacks\]\s+required = \[\]') {
            throw "Resource packs are still locked: $name"
        }
    }
    foreach ($name in @('minecraft/config/defaultoptions/options.txt', 'minecraft/config/defaultoptions/keybindings.txt',
            'minecraft/config/defaultoptions/servers.dat', 'packwiz-installer-bootstrap.jar',
            'client-update/Install-ClientUpdate.ps1', 'server-update/Install-ServerUpdate.ps1')) {
        if (-not $zip.GetEntry($name)) { throw "Required release entry missing: $name" }
    }
    if ($zip.GetEntry('minecraft/options.txt') -or $zip.GetEntry('minecraft/servers.dat')) {
        throw 'Personal options or server list must not be shipped at the game root.'
    }
    $defaultsMods = @($zip.Entries | Where-Object {
        $_.FullName -match '^(minecraft|client-update|server-update)/mods/defaultoptions-.*\.jar$'
    })
    if ($defaultsMods.Count -ne 2 -or $zip.GetEntry('server-update/mods/defaultoptions-fabric-1.20.1-18.0.6.jar')) {
        throw 'Default Options must be included exactly once per client payload and never on the server.'
    }
    foreach ($entry in $defaultsMods) {
        $hash = [Security.Cryptography.SHA512]::Create()
        $stream = $entry.Open()
        try { $actual = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '') }
        finally { $stream.Dispose(); $hash.Dispose() }
        if ($actual -ne (Get-FileHash $DefaultOptionsJar -Algorithm SHA512).Hash) {
            throw "Packaged Default Options hash mismatch: $($entry.FullName)"
        }
    }
    $instance = Read-Entry 'instance.cfg'
    if ($instance -notmatch ' -s client ' -or $instance -notmatch 'PreLaunchCommandIgnoreExitCode=false') {
        throw 'Automatic updates must be client-only and fail visibly.'
    }
    $expectedCommand = 'PreLaunchCommand="\"$INST_JAVA\" -jar \"$INST_DIR/packwiz-installer-bootstrap.jar\" -g -s client https://moelotusdead6-spec.github.io/the-gangs-modpack/pack.toml"'
    if (-not $instance.Contains($expectedCommand)) { throw 'Prism command quotes must survive Qt QSettings INI decoding.' }
    $options = Read-Entry 'minecraft/config/defaultoptions/options.txt'
    if ($options -notmatch '"file/immersive-interfaces.zip"') { throw 'Immersive Interfaces is not enabled by default.' }
    Write-Output "Verified $($zip.Entries.Count) ZIP entries; both search compass mods absent, GoldClaim and backpack compatibility jars match."
} finally { $zip.Dispose() }
Get-FileHash -LiteralPath $output -Algorithm SHA256
