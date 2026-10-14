[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GameDirectory,
    [string]$JavaExecutable,
    [switch]$ResetRswEnd
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$utf8 = [Text.UTF8Encoding]::new($false)
$running = @(Get-CimInstance Win32_Process |
    Where-Object {
        $_.ProcessId -ne $PID -and
        $_.CommandLine -and
        $_.CommandLine.IndexOf($game, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        $_.CommandLine.IndexOf('Install-ServerUpdate', [StringComparison]::OrdinalIgnoreCase) -lt 0 -and
        $_.CommandLine.IndexOf('Test-UpdateInstallers', [StringComparison]::OrdinalIgnoreCase) -lt 0 -and
        ($_.Name -in @('java.exe', 'javaw.exe', 'cmd.exe', 'powershell.exe', 'pwsh.exe'))
    })
if ($running.Count) { throw 'A process references this server directory. Stop the server before installing.' }
$isRepo = (Test-Path -LiteralPath (Join-Path $PSScriptRoot '..\artifacts') -PathType Container) -and
          (Test-Path -LiteralPath (Join-Path $PSScriptRoot '..\config') -PathType Container)
$repo = if ($isRepo) { (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path } else { $null }

$modsDir = if ($isRepo) { Join-Path $repo 'artifacts' } else { Join-Path $PSScriptRoot 'mods' }
$configDir = if ($isRepo) { Join-Path $repo 'config' } else { Join-Path $PSScriptRoot 'config' }
$purgeTool = if ($isRepo) {
    Join-Path $repo 'artifacts\gangs-teleport-purge-1.0.1.jar'
} else {
    Join-Path $PSScriptRoot 'tools\gangs-teleport-purge-1.0.1.jar'
}
$recoveryTool = if ($isRepo) {
    Join-Path $repo 'artifacts\gangs-player-recovery-1.0.0.jar'
} else {
    Join-Path $PSScriptRoot 'tools\gangs-player-recovery-1.0.0.jar'
}
if (-not $JavaExecutable) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $JavaExecutable = Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        $JavaExecutable = (Get-Command java.exe -CommandType Application -ErrorAction Stop).Source
    }
}
if (-not (Test-Path -LiteralPath $JavaExecutable -PathType Leaf)) { throw 'A Java 17+ executable is required.' }
if (-not (Test-Path -LiteralPath $purgeTool -PathType Leaf)) { throw 'Offline teleport purge tool is missing.' }
if (-not (Test-Path -LiteralPath $recoveryTool -PathType Leaf)) { throw 'Offline player recovery tool is missing.' }
& $JavaExecutable -jar $purgeTool --dry-run $game
if ($LASTEXITCODE -ne 0) { throw 'Offline teleport purge preflight failed; update aborted.' }
$releaseAdditions = @(
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
foreach ($jar in $releaseAdditions) {
    if (-not (Test-Path -LiteralPath (Join-Path $modsDir $jar) -PathType Leaf)) {
        throw "Required release mod is missing: $jar"
    }
}

$propertiesPath = Join-Path $game 'server.properties'
$corePath = Join-Path $game 'config\sophisticatedcore-common.toml'
$startPath = Join-Path $game 'start.bat'
foreach ($path in @($propertiesPath, $corePath, (Join-Path $game 'config\global_packs.toml'),
        (Join-Path $modsDir 'goldclaim-1.0.36.jar'),
        (Join-Path $modsDir 'gangshop-1.0.9.jar'),
        (Join-Path $modsDir 'soulsbackpackscompat-1.1.0.jar'),
        (Join-Path $modsDir 'gangscosmetics-2.0.2.jar'),
        (Join-Path $configDir 'gangscosmetics.json'),
        (Join-Path $PSScriptRoot 'server-settings.properties'),
        (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1'),
        (Join-Path $PSScriptRoot 'Prune-ServerBackups.ps1'),
        (Join-Path $configDir 'paxi\datapacks\gangs_kits\data\sophisticatedbackpacks\recipes\inception_upgrade.json'))) {
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
$end = $null
if ($ResetRswEnd) {
    $levelName = [regex]::Match($properties, '(?m)^level-name=([^\r\n]+)').Groups[1].Value.Trim()
    if (-not $levelName) { $levelName = 'world' }
    if ([IO.Path]::IsPathRooted($levelName) -or $levelName -match '(^|[\\/])\.\.([\\/]|$)') {
        throw 'Refusing to reset an RSW End outside this server directory.'
    }
    $end = Join-Path (Join-Path $game $levelName) 'dimensions\multiworld\rsw_the_end'
    if (Test-Path -LiteralPath $end) {
        $items = @((Get-Item -LiteralPath $end)) + @(Get-ChildItem -LiteralPath $end -Recurse -Force)
        if (@($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
            throw 'Refusing to reset RSW End containing a filesystem link/junction.'
        }
    }
}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $PSScriptRoot 'server-settings.properties'))) {
    if ($line -notmatch '^(view-distance|simulation-distance|require-resource-pack|difficulty)=(.+)$') {
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
$backupRoot = Join-Path $game 'backups'
if (Test-Path -LiteralPath $backupRoot) {
    $previous = @(Get-ChildItem -LiteralPath $backupRoot -Directory |
        Where-Object { $_.Name -match '^policy-v\d+\.\d+\.\d+-\d{8}-\d{6}-\d{4}$' } |
        Sort-Object { $_.Name -replace '^policy-v\d+\.\d+\.\d+-', '' })
    foreach ($old in @($previous | Select-Object -First ([Math]::Max(0, $previous.Count - 1)))) {
        Write-Output "Pruning oldest update backup: $($old.FullName)"
        Remove-Item -LiteralPath $old.FullName -Recurse -Force
    }
}
$backup = Join-Path $game ('backups\policy-v1.1.8-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
New-Item -ItemType Directory -Path $backup | Out-Null
& $JavaExecutable -jar $purgeTool --apply $game $backup
if ($LASTEXITCODE -ne 0) { throw "Offline teleport purge failed. Protected originals are in $backup; do not start the server." }
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $JavaExecutable -jar $recoveryTool --apply $game $backup 2>&1 | ForEach-Object { "$_" }
$recoveryExit = $LASTEXITCODE
$ErrorActionPreference = $previousPreference
$recoveryWarning = $null
if ($recoveryExit -ne 0) {
    $recoveryWarning = "WARNING: Kunoisback item recovery did not complete (exit $recoveryExit). Nothing was changed for that player; " +
        "the rest of the update is installed. Rerun with the server stopped: java -jar `"$recoveryTool`" --apply `"$game`" `"$backup`""
    Write-Warning $recoveryWarning
}
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
    $start = [regex]::Replace($start, 'goldclaim-\d+\.\d+\.\d+\.jar', 'goldclaim-1.0.36.jar')
    $start = [regex]::Replace($start, 'gangscosmetics-\d+\.\d+\.\d+\.jar', 'gangscosmetics-2.0.2.jar')
    $start = [regex]::Replace($start, 'gangshop-\d+\.\d+\.\d+\.jar', 'gangshop-1.0.9.jar')
    [IO.File]::WriteAllText($startPath, $start, $utf8)
}
$mods = Join-Path $game 'mods'
$removed = @(Get-ChildItem -LiteralPath $mods -File | Where-Object {
    $_.Name -match '^(goldclaim-.*\.jar(?:\.pending)?|gangshop-.*\.jar(?:\.pending)?|soulsbackpackscompat-.*\.jar(?:\.pending)?|gangshats-.*\.jar(?:\.pending)?|gangscosmetics-.*\.jar(?:\.pending)?|NaturesCompass-.*\.jar|ExplorersCompass-.*\.jar|jei-.*\.jar|YungsBetterEndIsland-.*\.jar|grimac-.*\.jar(?:\.pending)?)$'
})
foreach ($file in $removed) {
    Backup-File $file.FullName
    Remove-Item -LiteralPath $file.FullName
}
if ($ResetRswEnd) {
    if (Test-Path -LiteralPath $end -PathType Container) {
        Remove-Item -LiteralPath $end -Recurse -Force
        Write-Output 'Deleted only RSW End without a backup; it will regenerate on next start.'
    }
}
Copy-Item -LiteralPath (Join-Path $modsDir 'goldclaim-1.0.36.jar') -Destination $mods
Copy-Item -LiteralPath (Join-Path $modsDir 'gangshop-1.0.9.jar') -Destination $mods
Copy-Item -LiteralPath (Join-Path $modsDir 'soulsbackpackscompat-1.1.0.jar') -Destination $mods
Copy-Item -LiteralPath (Join-Path $modsDir 'gangscosmetics-2.0.2.jar') -Destination $mods
$additionalJars = @(
    'EndsPhantasm-1.20.1-1.4.jar',
    'Nullscape_1.20.x_v1.2.8.jar',
    'shield_overhaul-2.0.4.jar',
    'Necronomicon-Fabric-1.6.0+1.20.1.jar',
    'immersive_armors-1.7.2+1.20.1-fabric.jar',
    'RoughlyEnoughItems-12.1.785-fabric.jar',
    'bc_particle-0.0.6-fabric-1.20.1.jar'
)
foreach ($jar in $additionalJars) {
    $src = Join-Path $modsDir $jar
    if (Test-Path -LiteralPath $src -PathType Leaf) {
        Copy-Item -LiteralPath $src -Destination $mods -Force
    }
    foreach ($jar in $releaseAdditions) {
        Backup-File (Join-Path $mods $jar)
        Copy-Item -LiteralPath (Join-Path $modsDir $jar) -Destination $mods -Force
        if ((Get-FileHash (Join-Path $mods $jar)).Hash -ne (Get-FileHash (Join-Path $modsDir $jar)).Hash) {
            throw "Installed release mod hash mismatch: $jar"
        }
    }
}
Backup-File (Join-Path $game 'config\gangscosmetics.json')
Copy-Item -LiteralPath (Join-Path $configDir 'gangscosmetics.json') -Destination (Join-Path $game 'config')
[IO.File]::WriteAllText($corePath, $updatedCore, $utf8)
[IO.File]::WriteAllText($propertiesPath, $properties, $utf8)
$relative = 'paxi\datapacks\gangs_kits\data\sophisticatedbackpacks\recipes\inception_upgrade.json'
$destination = Join-Path $game ('config\' + $relative)
Backup-File $destination
New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($destination)) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $configDir $relative) -Destination $destination

$gangsFixesDir = Join-Path $configDir 'paxi\datapacks\gangs_fixes'
if (Test-Path -LiteralPath $gangsFixesDir -PathType Container) {
    foreach ($file in Get-ChildItem -LiteralPath $gangsFixesDir -Recurse -File) {
        $subPath = $file.FullName.Substring($configDir.Length).TrimStart('\')
        $dest = Join-Path (Join-Path $game 'config') $subPath
        Backup-File $dest
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($dest)) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $dest -Force
    }
}

$ecDest = Join-Path $game 'config\EssentialCommands.properties'
if (Test-Path -LiteralPath $ecDest -PathType Leaf) {
    Backup-File $ecDest
    $ecContent = [IO.File]::ReadAllText($ecDest)
    $pattern = '(?m)^rtp_enabled_worlds=.*$'
    $replacement = 'rtp_enabled_worlds=[minecraft:overworld, multiworld:rsw]'
    if ([regex]::IsMatch($ecContent, $pattern)) {
        $ecContent = [regex]::Replace($ecContent, $pattern, $replacement)
    } else {
        $ecContent = $ecContent.TrimEnd("`r", "`n") + "`r`n" + $replacement + "`r`n"
    }
    [IO.File]::WriteAllText($ecDest, $ecContent, $utf8)
} elseif (Test-Path -LiteralPath (Join-Path $configDir 'EssentialCommands.properties') -PathType Leaf) {
    Copy-Item -LiteralPath (Join-Path $configDir 'EssentialCommands.properties') -Destination $ecDest
}

$c2mePath = Join-Path $game 'config\c2me.toml'
$c2me = if (Test-Path -LiteralPath $c2mePath) { [IO.File]::ReadAllText($c2mePath) } else { '' }
$section = [regex]::Match($c2me, '(?ms)^\[threadedWorldGen\][^\r\n]*\r?\n.*?(?=^\[|\z)')
if ($section.Success) {
    $pattern = '(?m)^[ \t]*enabled[ \t]*=[^\r\n]*'
    if ([regex]::Matches($section.Value, $pattern).Count -ne 1) {
        throw 'Expected one threadedWorldGen.enabled setting; C2ME config was not changed.'
    }
    $updated = [regex]::Replace($section.Value, $pattern, 'enabled = false')
    $c2me = $c2me.Substring(0, $section.Index) + $updated + $c2me.Substring($section.Index + $section.Length)
} else {
    $c2me = $c2me.TrimEnd("`r", "`n") + "`n[threadedWorldGen]`nenabled = false`n"
}
Backup-File $c2mePath
[IO.File]::WriteAllText($c2mePath, $c2me, $utf8)
$lithiumPath = Join-Path $game 'config\lithium.properties'
$lithium = if (Test-Path -LiteralPath $lithiumPath) { [IO.File]::ReadAllText($lithiumPath) } else { '' }
$pattern = '(?m)^[ \t]*mixin\.util\.block_tracking[ \t]*=[^\r\n]*'
if ([regex]::Matches($lithium, $pattern).Count -gt 1) { throw 'Duplicate Lithium block tracking setting.' }
if ([regex]::IsMatch($lithium, $pattern)) {
    $lithium = [regex]::Replace($lithium, $pattern, 'mixin.util.block_tracking=false')
} else {
    $lithium = $lithium.TrimEnd("`r", "`n") + "`nmixin.util.block_tracking=false`n"
}
Backup-File $lithiumPath
[IO.File]::WriteAllText($lithiumPath, $lithium, $utf8)
$multiworldWorlds = Join-Path $game 'config\multiworld\worlds'
if (Test-Path -LiteralPath $multiworldWorlds -PathType Container) {
    foreach ($yml in Get-ChildItem -LiteralPath $multiworldWorlds -Recurse -File -Filter '*.yml') {
        $text = [IO.File]::ReadAllText($yml.FullName)
        $pattern = '(?m)^difficulty:[^\r\n]*'
        if ([regex]::Matches($text, $pattern).Count -gt 1) { throw "Duplicate Multiworld difficulty in $($yml.FullName)" }
        if ([regex]::IsMatch($text, $pattern)) {
            $updatedYml = [regex]::Replace($text, $pattern, 'difficulty: HARD')
        } else {
            $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
            $updatedYml = $text.TrimEnd("`r", "`n") + $newline + 'difficulty: HARD' + $newline
        }
        if ($updatedYml -ne $text) {
            Backup-File $yml.FullName
            [IO.File]::WriteAllText($yml.FullName, $updatedYml, $utf8)
        }
    }
}
& (Join-Path $PSScriptRoot 'Prune-ServerBackups.ps1') -GameDirectory $game
& (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1') -Target Server -GameDirectory $game
if ((Get-FileHash (Join-Path $mods 'goldclaim-1.0.36.jar')).Hash -ne
        (Get-FileHash (Join-Path $modsDir 'goldclaim-1.0.36.jar')).Hash) {
    throw 'Installed GoldClaim hash does not match the supplied update.'
}
if ((Get-FileHash (Join-Path $mods 'soulsbackpackscompat-1.1.0.jar')).Hash -ne
            (Get-FileHash (Join-Path $modsDir 'soulsbackpackscompat-1.1.0.jar')).Hash) {
        throw 'Installed Soulslike Backpacks Compatibility hash does not match the supplied update.'
}
if ((Get-FileHash (Join-Path $mods 'gangscosmetics-2.0.2.jar')).Hash -ne
            (Get-FileHash (Join-Path $modsDir 'gangscosmetics-2.0.2.jar')).Hash) {
    throw 'Installed Gangs Cosmetics hash does not match the supplied update.'
}
if ((Get-FileHash (Join-Path $mods 'gangshop-1.0.9.jar')).Hash -ne
        (Get-FileHash (Join-Path $modsDir 'gangshop-1.0.9.jar')).Hash) {
    throw 'Installed Gang Shop hash does not match the supplied update.'
}
if (@(Get-ChildItem -LiteralPath $mods -File | Where-Object {
    $_.Name -match '^gangshop-.*\.jar(?:\.pending)?$' -and $_.Name -ne 'gangshop-1.0.9.jar'
}).Count) { throw 'Old Gang Shop jars remain installed.' }
if (@(Get-ChildItem -LiteralPath $mods -File | Where-Object {
        $_.Name -match '^(NaturesCompass-.*\.jar|ExplorersCompass-.*\.jar|goldclaim-.*\.jar(?:\.pending)?|soulsbackpackscompat-.*\.jar(?:\.pending)?|gangshats-.*\.jar(?:\.pending)?|gangscosmetics-.*\.jar(?:\.pending)?|jei-.*\.jar|YungsBetterEndIsland-.*\.jar|grimac-.*\.jar(?:\.pending)?)$' -and
                $_.Name -notin @('goldclaim-1.0.36.jar', 'soulsbackpackscompat-1.1.0.jar', 'gangscosmetics-2.0.2.jar')
        }).Count) { throw 'Old or removed mod jars remain installed.' }
Write-Output "Server policy update installed. Backup: $backup"
Write-Output 'GoldClaim 1.0.36: RSW End portals now return players to their RSW spawn point or safe RSW ground near 0,0; every world runs on HARD and /difficulty applies server-wide (saved to server.properties).'
if ($recoveryWarning) { Write-Warning $recoveryWarning }
Write-Output 'GrimAC and its bundled PacketEvents removed; GrimAC cheat checks are disabled. BetterEnd, Lootr, GoldClaim protections, compatibility settings and backup limits remain. Active worlds, player data and the RSW journal were preserved unless an End reset was explicitly requested. Server remains stopped; start it yourself when ready.'
Write-Output 'GrimAC and its bundled PacketEvents removed; GrimAC cheat checks are disabled. BetterEnd, Lootr, GoldClaim protections, compatibility settings and backup limits remain. Active worlds, player data and the RSW journal were preserved unless an End reset was explicitly requested. Server remains stopped; start it yourself when ready.'
