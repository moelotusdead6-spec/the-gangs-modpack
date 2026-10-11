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
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
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
}
# The disposable instance is not owned by the user's running Prism process.
function Get-CimInstance {
    param([string]$ClassName)
    CimCmdlets\Get-CimInstance -ClassName $ClassName | Where-Object { $_.Name -ne 'prismlauncher.exe' }
}
foreach ($run in 1..2) {
    & (Join-Path $root 'client-update\Install-ClientUpdate.ps1') -GameDirectory $game
    Assert-Preserved $hashes
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
Write-Fixture (Join-Path $server 'start.bat') 'java -jar goldclaim-1.0.30.jar'
Write-Fixture (Join-Path $server 'config\sophisticatedcore-common.toml') 'enabledItems = ["sophisticatedbackpacks:inception_upgrade|true", "other:item|true"]'
Write-Fixture (Join-Path $server 'config\global_packs.toml') "[resourcepacks]`r`nrequired = [`"legacy.zip`"]`r`n"
Write-Fixture (Join-Path $server 'mods\goldclaim-1.0.30.jar') 'old-fixture-jar'
$hashes = @{}
foreach ($relative in @('world\level.dat', 'world\playerdata\fixture.dat', 'config\goldclaim\claims.json',
        'config\goldclaim\rsw-reset.json', 'config\multiworld\worlds\multiworld\rsw.yml',
        'config\personal-server-mod.json')) {
    $path = Join-Path $server $relative
    Write-Fixture $path 'untouched-fixture'
    $hashes[$path] = (Get-FileHash -LiteralPath $path).Hash
}
& (Join-Path $root 'server-update\Install-ServerUpdate.ps1') -GameDirectory $server
Assert-Preserved $hashes
Require (Test-Path -LiteralPath (Join-Path $server 'mods\goldclaim-1.0.31.jar')) 'New GoldClaim is missing.'
Require (-not (Test-Path -LiteralPath (Join-Path $server 'mods\goldclaim-1.0.30.jar'))) 'Old GoldClaim remains.'
$properties = [IO.File]::ReadAllText((Join-Path $server 'server.properties'))
Require ($properties -match 'server-port=25588' -and $properties -match 'motd=Keep me') 'Unrelated server properties changed.'
Require ($properties -match 'view-distance=6' -and $properties -match 'simulation-distance=6') 'Server policy was not applied.'
Write-Output 'PASS: byte-for-byte client preferences/server-list retention on first install and rerun; Prism hook placement/idempotence; default server NBT; stopped-server installation with worlds, player data, claims and RSW journal/config preserved.'
Remove-Item -LiteralPath $root -Recurse
