[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$GameDirectory
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$instance = [IO.Directory]::GetParent($game).FullName
$config = Join-Path $instance 'instance.cfg'
foreach ($required in @($config, (Join-Path $instance 'packwiz-installer-bootstrap.jar'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Select the existing Prism game directory. Required file missing: $required"
    }
}
$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'prismlauncher.exe' -or
        ($_.Name -in @('java.exe', 'javaw.exe') -and $_.CommandLine -and
        $_.CommandLine.IndexOf($game, [StringComparison]::OrdinalIgnoreCase) -ge 0)
})
if ($running.Count) { throw 'Close Prism Launcher and Minecraft before repairing; Prism may overwrite external configuration edits while open.' }
. (Join-Path $PSScriptRoot 'Get-PrismAutoUpdateConfig.ps1')
$updated = Get-PrismAutoUpdateConfig ([IO.File]::ReadAllText($config))
Copy-Item -LiteralPath $config -Destination ($config + '.backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
[IO.File]::WriteAllText($config, $updated, [Text.UTF8Encoding]::new($false))
Write-Output 'Prism pre-launch quoting repaired. Reopen Prism and press Play in the same instance. Game settings, keybindings, server lists and mods were not changed.'
