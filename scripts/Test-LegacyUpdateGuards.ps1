[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('gangs-update-guard-' + [guid]::NewGuid().ToString('N'))
$mods = Join-Path $scratch 'mods'
$active = Join-Path $mods 'goldclaim-1.0.63.jar'
$pending = Join-Path $mods 'goldclaim-1.0.64.jar.pending'
$output = Join-Path $scratch 'release.zip'

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Require-LegacyInstallerRejection([string]$ExpectedJar) {
    $failure = $null
    try { & (Join-Path $PSScriptRoot 'Install-ServerUpdate.ps1') -GameDirectory $scratch }
    catch { $failure = $_ }
    Require ($null -ne $failure -and $failure.Exception.Message -like "*would downgrade or remove $ExpectedJar*") `
        "Historical installer did not reject $ExpectedJar."
}

try {
    New-Item -ItemType Directory -Path $mods | Out-Null
    [IO.File]::WriteAllText($active, 'active fixture')
    [IO.File]::WriteAllText($pending, 'pending fixture')
    $failure = $null
    try {
        & (Join-Path $PSScriptRoot 'Build-AllInOneRelease.ps1') `
            -BaselineZip (Join-Path $repo 'artifacts\neon-wall-wonders-gwbzt3mk.jar') -OutputZip $output
    } catch { $failure = $_ }
    Require ($null -ne $failure -and $failure.Exception.Message -like '*Historical all-in-one builder packages GoldClaim 1.0.44*') `
        'Historical release builder did not reject the newer pack.'
    Require (-not (Test-Path -LiteralPath $output)) 'Rejected builder created an output ZIP.'
    $launcher = Join-Path $repo 'artifacts\gangs-neon-loot-restart-1.0.64.jar'
    $launcherHash = (Get-FileHash -LiteralPath $launcher -Algorithm SHA256).Hash
    $failure = $null
    try { & (Join-Path $PSScriptRoot 'hosted\Build-NeonLootRestart.ps1') }
    catch { $failure = $_ }
    Require ($null -ne $failure -and $failure.Exception.Message -like '*Historical Neon loot launcher targets GoldClaim 1.0.63*') `
        'Historical Neon loot builder did not reject the newer pack.'
    Require ((Get-FileHash -LiteralPath $launcher -Algorithm SHA256).Hash -ceq $launcherHash) `
        'Rejected Neon loot builder changed the historical launcher.'
    Require-LegacyInstallerRejection 'goldclaim-1.0.63.jar'
    Require ([IO.File]::ReadAllText($active) -ceq 'active fixture') 'Rejected installer changed the active jar.'
    Require ([IO.File]::ReadAllText($pending) -ceq 'pending fixture') 'Rejected installer changed the pending jar.'
    Remove-Item -LiteralPath $active
    Require-LegacyInstallerRejection 'goldclaim-1.0.64.jar.pending'
    Require ([IO.File]::ReadAllText($pending) -ceq 'pending fixture') 'Rejected installer changed the pending jar.'
    Write-Output 'PASS: historical builders and installer reject incompatible updates without creating or changing files.'
} finally {
    if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output }
    if (Test-Path -LiteralPath $active) { Remove-Item -LiteralPath $active }
    if (Test-Path -LiteralPath $pending) { Remove-Item -LiteralPath $pending }
    if (Test-Path -LiteralPath $mods) { Remove-Item -LiteralPath $mods }
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch }
}
