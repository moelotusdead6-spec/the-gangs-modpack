[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GameDirectory
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$mods = Join-Path $game 'mods'
if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw "Client mods folder not found: $mods" }
$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'prismlauncher.exe' -or
        ($_.ProcessId -ne $PID -and $_.Name -in @('java.exe', 'javaw.exe') -and
        $_.CommandLine -and $_.CommandLine.IndexOf($game, [StringComparison]::OrdinalIgnoreCase) -ge 0)
})
if ($running.Count) { throw 'Close Prism Launcher and Minecraft before installing; Prism can overwrite external configuration edits while open.' }
$instance = [IO.Directory]::GetParent($game).FullName
$instanceConfig = Join-Path $instance 'instance.cfg'
$bootstrap = Join-Path $instance 'packwiz-installer-bootstrap.jar'
foreach ($required in @($instanceConfig, $bootstrap)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Automatic updates require the Prism instance game directory and bootstrap: $required"
    }
}
. (Join-Path $PSScriptRoot 'Get-PrismAutoUpdateConfig.ps1')
$updatedInstanceConfig = Get-PrismAutoUpdateConfig ([IO.File]::ReadAllText($instanceConfig))
$compat = Join-Path $PSScriptRoot 'mods\soulsbackpackscompat-1.1.0.jar'
$cosmetics = Join-Path $PSScriptRoot 'mods\gangscosmetics-2.0.2.jar'
$defaultsMod = Join-Path $PSScriptRoot 'mods\defaultoptions-fabric-1.20.1-18.0.6.jar'
$defaults = Join-Path $PSScriptRoot 'config\defaultoptions'
foreach ($required in @($compat, $cosmetics, $defaultsMod,
        (Join-Path $defaults 'options.txt'), (Join-Path $defaults 'keybindings.txt'),
        (Join-Path $defaults 'servers.dat'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Client update jar not found: $required" }
}
$removed = @(Get-ChildItem -LiteralPath $mods -File | Where-Object {
    $_.Name -match '^(NaturesCompass-|ExplorersCompass-).*\.jar$' -or
        $_.Name -match '^soulsbackpackscompat-.*\.jar(?:\.pending)?$' -or
        $_.Name -match '^defaultoptions-.*\.jar(?:\.pending)?$' -or
        $_.Name -match '^gangscosmetics-.*\.jar(?:\.pending)?$' -or
        $_.Name -match '^grimac-.*\.jar(?:\.pending)?$'
})
if ($removed.Count) {
    $backup = Join-Path $game ('backups\client-mod-update-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
    New-Item -ItemType Directory -Path $backup | Out-Null
    foreach ($file in $removed) { Move-Item -LiteralPath $file.FullName -Destination $backup }
}
Copy-Item -LiteralPath $compat -Destination $mods
Copy-Item -LiteralPath $cosmetics -Destination $mods
Copy-Item -LiteralPath $defaultsMod -Destination $mods
$defaultsTarget = Join-Path $game 'config\defaultoptions'
New-Item -ItemType Directory -Path $defaultsTarget -Force | Out-Null
foreach ($name in @('options.txt', 'keybindings.txt', 'servers.dat')) {
    $destination = Join-Path $defaultsTarget $name
    if (Test-Path -LiteralPath $destination) {
        Copy-Item -LiteralPath $destination -Destination ($destination + '.backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
    }
    Copy-Item -LiteralPath (Join-Path $defaults $name) -Destination $destination
}
if ((Get-FileHash (Join-Path $mods 'defaultoptions-fabric-1.20.1-18.0.6.jar')).Hash -ne
        (Get-FileHash $defaultsMod).Hash) { throw 'Installed Default Options jar does not match the supplied update.' }
if ((Get-FileHash (Join-Path $mods 'soulsbackpackscompat-1.1.0.jar')).Hash -ne
        (Get-FileHash $compat).Hash) { throw 'Installed compatibility jar does not match the supplied update.' }
if ((Get-FileHash (Join-Path $mods 'gangscosmetics-2.0.2.jar')).Hash -ne
        (Get-FileHash $cosmetics).Hash) { throw 'Installed cosmetics jar does not match the supplied update.' }
& (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1') -Target Client -GameDirectory $game
Copy-Item -LiteralPath $instanceConfig -Destination ($instanceConfig + '.backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
[IO.File]::WriteAllText($instanceConfig, $updatedInstanceConfig, [Text.UTF8Encoding]::new($false))
Write-Output 'Client setup complete. Press Play in this same Prism instance for future automatic client-only updates. Personal options and saved servers were not overwritten; update failures block launch and appear in the launcher log.'
