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
if ($running.Count) { throw 'Close Prism Launcher and Minecraft before removing JEI.' }
$removed = @(Get-ChildItem -LiteralPath $mods -File | Where-Object {
    $_.Name -match '^jei-.*\.jar(?:\.pending)?$'
})
if ($removed.Count) {
    $backup = Join-Path $game ('backups\client-jei-removal-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
    New-Item -ItemType Directory -Path $backup | Out-Null
    foreach ($file in $removed) { Move-Item -LiteralPath $file.FullName -Destination $backup }
    Write-Output "Removed $($removed.Count) JEI file(s). Backup: $backup"
} else {
    Write-Output 'No JEI jars found in this client.'
}
Write-Output 'REI and all other mods and personal settings were left unchanged.'
