[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GameDirectory
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$mods = Join-Path $game 'mods'
if (-not (Test-Path -LiteralPath $mods -PathType Container)) { throw "Client mods folder not found: $mods" }
$removed = @(Get-ChildItem -LiteralPath $mods -File | Where-Object {
    $_.Name -match '^(NaturesCompass-|ExplorersCompass-).*\.jar$'
})
if ($removed.Count) {
    $backup = Join-Path $game ('backups\removed-compasses-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
    New-Item -ItemType Directory -Path $backup | Out-Null
    foreach ($file in $removed) { Move-Item -LiteralPath $file.FullName -Destination $backup }
}
& (Join-Path $PSScriptRoot 'Install-ResourcePackDefaults.ps1') -Target Client -GameDirectory $game
Write-Output 'Client updated. Both search compass mods are removed; existing resource-pack choices are preserved after first application.'
