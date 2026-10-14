[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseZip
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$root = Join-Path $repo ('tmp\update-installer-test-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
New-Item -ItemType Directory -Path $root | Out-Null
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ReleaseZip).Path)
try {
    foreach ($entry in $zip.Entries | Where-Object {
        $_.FullName -match '^(client-update|server-update)/' -and $_.Name
    }) {
        $path = Join-Path $root $entry.FullName.Replace('/', '\')
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($path)) -Force | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $path)
    }
} finally { $zip.Dispose() }

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Write-Fixture([string]$Path, [string]$Content) {
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($Path)) -Force | Out-Null
    if ($Path -match '\\world\\playerdata\\.*\.dat$' -or $Path -match '\\world\\data\\waystones\.dat$') {
        $bytes = [byte[]]@(10, 0, 0, 0)
        if ($Path -match '\\waystones\.dat$') {
            $bytes = [byte[]]@(10, 0, 0, 10, 0, 4, 100, 97, 116, 97,
                9, 0, 9, 87, 97, 121, 115, 116, 111, 110, 101, 115, 0, 0, 0, 0, 0, 0, 0)
        }
        $stream = [IO.File]::Create($Path)
        $gzip = [IO.Compression.GzipStream]::new($stream, [IO.Compression.CompressionMode]::Compress)
        try { $gzip.Write($bytes, 0, $bytes.Length) } finally { $gzip.Dispose(); $stream.Dispose() }
    } else {
        [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
    }
}
function Assert-Preserved([hashtable]$Hashes) {
    foreach ($path in $Hashes.Keys) {
        Require ((Get-FileHash -LiteralPath $path).Hash -eq $Hashes[$path]) "Personal data changed: $path"
    }
}
$instance = Join-Path $root 'existing-instance'
$game = Join-Path $instance 'minecraft'
New-Item -ItemType Directory -Path (Join-Path $game 'mods') -Force | Out-Null
Write-Fixture (Join-Path $instance 'instance.cfg') "[General]`r`nname=Personal Instance`r`n[Java]`r`nOverrideJavaLocation=false`r`nOverrideCommands=true`r`nPreLaunchCommand=old`r`nPreLaunchCommandIgnoreExitCode=true`r`n"
Write-Fixture (Join-Path $instance 'packwiz-installer-bootstrap.jar') 'fixture-only-not-executed'
Write-Fixture (Join-Path $game 'config\global_packs.toml') "[resourcepacks]`r`nrequired = [`"legacy.zip`"]`r`n"
$personal = @{
    'options.txt' = "key_key.forward:key.keyboard.up`r`ngamma:0.9`r`nresourcePacks:[]`r`n"
    'servers.dat' = 'binary-server-list-sentinel'
    'servers.dat_old' = 'previous-server-list-sentinel'
    'config\personal-mod.json' = '{"preference":"keep"}'
    'defaultoptions.journal.json' = '{"journal":"keep"}'
}
$hashes = @{}
Write-Fixture (Join-Path $game 'mods\grimac-fabric-2.3.74-fixture.jar') 'removed-client-anticheat'
Write-Fixture (Join-Path $game 'mods\badpackets-fabric-fixture.jar') 'unrelated-client-library'
$hashes[(Join-Path $game 'mods\badpackets-fabric-fixture.jar')] =
    (Get-FileHash (Join-Path $game 'mods\badpackets-fabric-fixture.jar')).Hash
foreach ($relative in $personal.Keys) {
    $path = Join-Path $game $relative
    Write-Fixture $path $personal[$relative]
    $hashes[$path] = (Get-FileHash -LiteralPath $path).Hash
}
function Get-CimInstance {
    param([string]$ClassName)
    [pscustomobject]@{ Name = 'prismlauncher.exe'; ProcessId = 1; CommandLine = '' }
}
foreach ($script in @('Install-ClientUpdate.ps1', 'Repair-PrismAutoUpdate.ps1')) {
    $before = (Get-FileHash (Join-Path $instance 'instance.cfg')).Hash
    try {
        & (Join-Path $root ('client-update\' + $script)) -GameDirectory $game
        throw 'Installer failed to reject an open Prism Launcher.'
    } catch {
        if ($_.Exception.Message -notmatch '^Close Prism Launcher and Minecraft') { throw }
    }
    Require ((Get-FileHash (Join-Path $instance 'instance.cfg')).Hash -ceq $before) 'Open-launcher rejection changed configuration.'
    Assert-Preserved $hashes
    Require (Test-Path (Join-Path $game 'mods\grimac-fabric-2.3.74-fixture.jar')) 'Open-launcher rejection removed a mod.'
}
# The disposable instance is not owned by the user's running Prism process.
function Get-CimInstance {
    param([string]$ClassName)
    CimCmdlets\Get-CimInstance -ClassName $ClassName | Where-Object { $_.Name -ne 'prismlauncher.exe' }
}
foreach ($run in 1..2) {
    & (Join-Path $root 'client-update\Install-ClientUpdate.ps1') -GameDirectory $game
    Assert-Preserved $hashes
    Require (-not (Test-Path (Join-Path $game 'mods\grimac-fabric-2.3.74-fixture.jar'))) 'Manual client installer retained GrimAC.'
}
$cfg = [IO.File]::ReadAllText((Join-Path $instance 'instance.cfg'))
Require ($cfg -match '(?ms)^\[General\]\s+OverrideCommands=true\s+PreLaunchCommand=.* -s client .*?\s+PreLaunchCommandIgnoreExitCode=false') 'Prism auto-update keys are not in [General].'
Require ([regex]::Matches($cfg, '(?m)^PreLaunchCommand=').Count -eq 1) 'Duplicate prelaunch commands.'
Require ($cfg -match 'name=Personal Instance') 'Personal instance name changed.'
. (Join-Path $PSScriptRoot 'Get-PrismAutoUpdateConfig.ps1')
Require ((Get-PrismAutoUpdateConfig $cfg) -ceq $cfg) 'Prism update configuration is not idempotent.'
& (Join-Path $root 'client-update\Repair-PrismAutoUpdate.ps1') -GameDirectory $game
Assert-Preserved $hashes
Require ([IO.File]::ReadAllText((Join-Path $instance 'instance.cfg')) -ceq $cfg) 'Launcher-only repair changed an already corrected configuration.'

$servers = [IO.File]::ReadAllBytes((Join-Path $game 'config\defaultoptions\servers.dat'))
$reader = [IO.BinaryReader]::new([IO.MemoryStream]::new($servers))
function Read-NbtString {
    $length = ([int]$reader.ReadByte() -shl 8) -bor [int]$reader.ReadByte()
    return [Text.Encoding]::UTF8.GetString($reader.ReadBytes($length))
}
try {
    Require ($reader.ReadByte() -eq 10) 'Default server NBT must be an uncompressed compound.'
    Require ((Read-NbtString) -ceq '') 'Unexpected root NBT name.'
    Require ($reader.ReadByte() -eq 9) 'Missing servers list.'
    Require ((Read-NbtString) -ceq 'servers') 'Wrong server list key.'
    Require ($reader.ReadByte() -eq 10) 'Server entries must be compounds.'
    Require ([BitConverter]::ToString($reader.ReadBytes(4)) -ceq '00-00-00-01') 'Expected exactly one default server.'
    foreach ($key in @('name', 'ip')) {
        Require ($reader.ReadByte() -eq 8) 'Expected NBT string tag.'
        Require ((Read-NbtString) -ceq $key) 'Unexpected server NBT key.'
        $expected = if ($key -eq 'name') { 'The Gangs Modded Adventure' } else { 'gangs-adventure.playit.plus' }
        Require ((Read-NbtString) -ceq $expected) 'Wrong public server default.'
    }
    Require ($reader.ReadByte() -eq 0 -and $reader.ReadByte() -eq 0) 'Missing NBT compound terminators.'
    Require ($reader.BaseStream.Position -eq $reader.BaseStream.Length) 'Unexpected trailing NBT bytes.'
} finally { $reader.Dispose() }

$server = Join-Path $root 'stopped-server'
New-Item -ItemType Directory -Path (Join-Path $server 'mods') -Force | Out-Null
Write-Fixture (Join-Path $server 'server.properties') "motd=Keep me`r`nview-distance=10`r`nsimulation-distance=8`r`nrequire-resource-pack=true`r`nserver-port=25588`r`n"
Write-Fixture (Join-Path $server 'start.bat') 'java -jar goldclaim-1.0.31.jar'
Write-Fixture (Join-Path $server 'config\sophisticatedcore-common.toml') 'enabledItems = ["sophisticatedbackpacks:inception_upgrade|true", "other:item|true"]'
Write-Fixture (Join-Path $server 'config\global_packs.toml') "[resourcepacks]`r`nrequired = [`"legacy.zip`"]`r`n"
Write-Fixture (Join-Path $server 'config\c2me.toml') "[threadedWorldGen]`nenabled = `"default`"`nother = true`n[ioSystem]`nenabled = true`n"
Write-Fixture (Join-Path $server 'config\lithium.properties') "mixin.util.block_tracking=true`nmixin.math.sine_lut=false`n"
Write-Fixture (Join-Path $server 'mods\goldclaim-1.0.31.jar') 'old-fixture-jar'
Write-Fixture (Join-Path $server 'mods\gangshop-1.0.8.jar') 'old-shop-jar'
Write-Fixture (Join-Path $server 'mods\YungsBetterEndIsland-1.20-Fabric-2.0.6.jar') 'removed-island-mod'
Write-Fixture (Join-Path $server 'mods\tru.e-ending-v1.1.0c.jar') 'preserved-dragon-overhaul'
Write-Fixture (Join-Path $server 'mods\grimac-fabric-2.3.74-fixture.jar') 'removed-server-anticheat'
Write-Fixture (Join-Path $server 'mods\grimac-fabric-2.3.74-fixture.jar.pending') 'removed-pending-anticheat'
Write-Fixture (Join-Path $server 'world\dimensions\multiworld\rsw_the_end\region\fixture.mca') 'old-arena'
foreach ($stamp in @('20261001-120000-0000', '20261002-120000-0000', '20261003-120000-0000')) {
    Write-Fixture (Join-Path $server "backups\policy-v1.1.3-$stamp\fixture.txt") 'old-backup'
}
$hashes = @{}
foreach ($relative in @('world\level.dat', 'world\playerdata\fixture.dat', 'config\goldclaim\claims.json',
        'config\personal-server-mod.json')) {
    $path = Join-Path $server $relative
    Write-Fixture $path 'untouched-fixture'
    $hashes[$path] = (Get-FileHash -LiteralPath $path).Hash
}
foreach ($relative in @('mods\better-end-fixture.jar', 'mods\lootr-fixture.jar',
        'mods\badpackets-fabric-fixture.jar', 'config\GrimAC\config.yml')) {
    $path = Join-Path $server $relative
    Write-Fixture $path 'preserve-content-and-settings'
    $hashes[$path] = (Get-FileHash $path).Hash
}
$journal = Join-Path $server 'config\goldclaim\rsw-reset.json'
Write-Fixture $journal '{"phase":"IDLE","generation":8}'
$hashes[$journal] = (Get-FileHash $journal).Hash
Write-Fixture (Join-Path $server 'world\dimensions\multiworld\rsw-reset-backups\reset-123\world\rsw\fixture.mca') 'obsolete-reset'
foreach ($stamp in @('20261001-120000', '20261002-120000', '20261003-120000')) {
    Write-Fixture (Join-Path $server "backups\personal-boots-rsw-$stamp\fixture.txt") 'old-other-backup'
}
foreach ($relative in @('config\gangshop\prices.json', 'config\gangshop\category_settings.json',
        'world\data\gangbucks_wallets.dat', 'world\data\waystones.dat', 'world\playerdata\offline.dat',
        'world\playerdata\07337021-ea67-4593-b127-73f4261b1134.dat')) {
    $path = Join-Path $server $relative
    Write-Fixture $path 'preserve-shop-and-waystone-data-before-startup'
    $hashes[$path] = (Get-FileHash -LiteralPath $path).Hash
}
Write-Fixture (Join-Path $server 'config\multiworld\worlds\minecraft\overworld.yml') "environment: NORMAL`r`ndifficulty: EASY`r`nseed: 0`r`n"
Write-Fixture (Join-Path $server 'config\multiworld\worlds\multiworld\rsw.yml') "namespace: multiworld`npath: rsw`nenvironment: NORMAL`nseed: 42`n"
Write-Fixture (Join-Path $server 'config\multiworld\portals.yml') "portals:`n"
$portalsHash = (Get-FileHash (Join-Path $server 'config\multiworld\portals.yml')).Hash
$installOutput = @(& (Join-Path $root 'server-update\Install-ServerUpdate.ps1') -GameDirectory $server -ResetRswEnd 3>&1 | ForEach-Object { "$_" })
$installOutput | Write-Output
Require (@($installOutput | Where-Object { $_ -match 'Pinned pre-loss snapshot for Kunoisback was not found' }).Count -eq 1) 'Recovery tool did not run or report the missing source.'
Require (@($installOutput | Where-Object { $_ -match 'Kunoisback item recovery did not complete \(exit 3\)' }).Count -ge 1) 'Missing recovery source must warn without aborting.'
Require (-not (Test-Path -LiteralPath (Join-Path $server 'world\goldclaim-recovery'))) 'Recovery marker written without a recovery.'
Assert-Preserved $hashes
Require (Test-Path -LiteralPath (Join-Path $server 'mods\goldclaim-1.0.36.jar')) 'New GoldClaim is missing.'
Require (-not (Test-Path -LiteralPath (Join-Path $server 'mods\goldclaim-1.0.31.jar'))) 'Old GoldClaim remains.'
Require (-not (Test-Path -LiteralPath (Join-Path $server 'mods\gangshop-1.0.8.jar'))) 'Old Gang Shop remains.'
Require (-not (Test-Path -LiteralPath (Join-Path $server 'mods\YungsBetterEndIsland-1.20-Fabric-2.0.6.jar'))) 'Better End Island remains.'
Require (-not @(Get-ChildItem (Join-Path $server 'mods') -File | Where-Object {
    $_.Name -match '^grimac-.*\.jar(?:\.pending)?$'
}).Count) 'GrimAC remains installed.'
Require ([IO.File]::ReadAllText((Join-Path $server 'mods\tru.e-ending-v1.1.0c.jar')) -eq 'preserved-dragon-overhaul') 'True Ending was changed.'
Require (-not (Test-Path -LiteralPath (Join-Path $server 'world\dimensions\multiworld\rsw_the_end'))) 'RSW End was not archived.'
$backups = @(Get-ChildItem -LiteralPath (Join-Path $server 'backups') -Directory)
Require (@($backups | Where-Object { $_.Name -match '^policy-' }).Count -eq 2) 'Update backup retention must keep two backups.'
Require (@($backups | Where-Object { $_.Name -match '^personal-boots-rsw-' }).Count -eq 2) 'Other backup categories must keep two backups.'
Require (-not (Test-Path -LiteralPath (Join-Path $server 'backups\policy-v1.1.3-20261001-120000-0000'))) 'Oldest update backup was not pruned.'
Require (-not @(Get-ChildItem (Join-Path $server 'world\dimensions\multiworld\rsw-reset-backups') -Directory).Count) 'RSW reset backups remain.'
Require (-not @(Get-ChildItem (Join-Path $server 'backups') -Recurse -Directory | Where-Object { $_.Name -eq 'rsw_the_end' }).Count) 'An RSW End backup remains.'
Require ([IO.File]::ReadAllText((Join-Path $server 'config\c2me.toml')) -match '(?s)\[threadedWorldGen\]\nenabled = false\nother = true\n\[ioSystem\]\nenabled = true') 'C2ME patch changed unrelated settings.'
Require ([IO.File]::ReadAllText((Join-Path $server 'config\lithium.properties')) -match 'mixin.math.sine_lut=false') 'Lithium patch changed unrelated settings.'
Require ([IO.File]::ReadAllText((Join-Path $server 'config\lithium.properties')) -match 'mixin.util.block_tracking=false') 'Lithium compatibility switch missing.'
$properties = [IO.File]::ReadAllText((Join-Path $server 'server.properties'))
Require ($properties -match 'server-port=25588' -and $properties -match 'motd=Keep me') 'Unrelated server properties changed.'
Require ($properties -match 'view-distance=6' -and $properties -match 'simulation-distance=6') 'Server policy was not applied.'
Require ($properties -match '(?m)^difficulty=hard\r?$' -and ([regex]::Matches($properties, '(?m)^difficulty=')).Count -eq 1) 'Server difficulty was not set to hard.'
Require ([IO.File]::ReadAllText((Join-Path $server 'config\multiworld\worlds\minecraft\overworld.yml')) -ceq "environment: NORMAL`r`ndifficulty: HARD`r`nseed: 0`r`n") 'Overworld Multiworld difficulty was not set to HARD.'
Require ([IO.File]::ReadAllText((Join-Path $server 'config\multiworld\worlds\multiworld\rsw.yml')) -ceq "namespace: multiworld`npath: rsw`nenvironment: NORMAL`nseed: 42`ndifficulty: HARD`n") 'RSW Multiworld difficulty was not set to HARD.'
Require ((Get-FileHash (Join-Path $server 'config\multiworld\portals.yml')).Hash -eq $portalsHash) 'Non-world Multiworld config changed.'
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $root 'server-update\mods') -Filter '*.jar' |
        Where-Object { $_.Name -match '^(supplementaries-|suppsquared-|EnderZoology-|better-cities-|better-end-|bclib-|mcw-|elytraslot-|gangshop-|goldclaim-)' }) {
    Require ((Get-FileHash (Join-Path $server ('mods\' + $file.Name))).Hash -eq
        (Get-FileHash $file.FullName).Hash) "Server payload mod missing or changed: $($file.Name)"
}
Write-Fixture (Join-Path $server 'world\dimensions\multiworld\rsw_the_end\region\fixture.mca') 'keep-new-end'
$endHash = (Get-FileHash (Join-Path $server 'world\dimensions\multiworld\rsw_the_end\region\fixture.mca')).Hash
& (Join-Path $root 'server-update\Install-ServerUpdate.ps1') -GameDirectory $server
Assert-Preserved $hashes
Require ((Get-FileHash (Join-Path $server 'world\dimensions\multiworld\rsw_the_end\region\fixture.mca')).Hash -eq
    $endHash) 'Normal mod installation reset the End without approval.'
Write-Output 'PASS: byte-for-byte client preferences/server-list retention on first install and rerun; Prism hook placement/idempotence; default server NBT; stopped-server installation with worlds, player data, claims and RSW journal/config preserved.'
Remove-Item -LiteralPath $root -Recurse
