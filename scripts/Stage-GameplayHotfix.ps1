[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$GameDirectory,
    [ValidateSet('1.0.39', '1.0.40')][string]$Version = '1.0.39'
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$previousVersion = if ($Version -eq '1.0.40') { '1.0.39' } else { '1.0.38' }
$jar = Join-Path $repo "artifacts\goldclaim-$Version.jar"
$table = Join-Path $repo 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'
$collections = Join-Path $repo 'config\randomcollections\config.json'
$previousJar = Join-Path $game "mods\goldclaim-$previousVersion.jar"
$previousTable = Join-Path $game 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'
$previousCollections = Join-Path $game 'config\randomcollections\config.json'
$installer = Join-Path $PSScriptRoot 'Install-PendingGameplayHotfix.ps1'
$launcherPath = Join-Path $game 'start.bat'
foreach ($path in @($jar, $table, $collections, $previousJar, $previousTable, $previousCollections, $installer, $launcherPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required hotfix file missing: $path" }
}
$jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
$previousJarHash = (Get-FileHash -LiteralPath $previousJar -Algorithm SHA256).Hash
$tableHash = (Get-FileHash -LiteralPath $table -Algorithm SHA256).Hash
$launcher = [IO.File]::ReadAllText($launcherPath)
if (-not $launcher.Contains("goldclaim-$previousVersion.jar") -or -not $launcher.Contains($previousJarHash)) {
    throw "Expected the checksum-pinned GoldClaim $previousVersion launcher. Refusing unexpected launcher changes."
}
$label = ':gangs_launcher'
$labelOffset = $launcher.IndexOf("`r`n$label`r`n", [StringComparison]::Ordinal) + 2
if ($labelOffset -lt 2) { throw 'Launcher has no reserved hotfix padding.' }
$javaLine = '"%JAVA_EXE%" @%JVM_ARGS_FILE% -jar fabric-server-launch.jar nogui'
$javaOffset = $launcher.IndexOf($javaLine, [StringComparison]::Ordinal)
if ($javaOffset -lt 0) { throw 'Cannot locate the existing server launch command.' }
$body = $launcher.Substring($labelOffset)
$hookBase = "powershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Install-PendingGameplayHotfix.ps1`" -GameDirectory `"%~dp0.`""
$hookTail = "`r`nif errorlevel 1 exit /b 1`r`n"
if ($body.Contains('Install-PendingGameplayHotfix.ps1')) {
    if ($body.Contains("$hookBase -Version $previousVersion$hookTail")) {
        $body = $body.Replace("$hookBase -Version $previousVersion$hookTail", '')
    } elseif ($previousVersion -eq '1.0.39' -and $body.Contains("$hookBase$hookTail")) {
        $body = $body.Replace("$hookBase$hookTail", '')
    } else {
        throw 'Unexpected pending-hotfix startup hook. Nothing was staged.'
    }
}
$body = $body.Replace("goldclaim-$previousVersion", "goldclaim-$Version").Replace("GoldClaim $previousVersion", "GoldClaim $Version")
$body = $body.Replace($previousJarHash, $jarHash)
$hook = "$hookBase -Version $Version$hookTail"
$body = $body.Insert($label.Length + 2, $hook)
$head = "@echo off`r`ngoto :gangs_launcher`r`n"
$bodyGrowth = $body.Length - $launcher.Substring($labelOffset).Length
$padding = $labelOffset - $bodyGrowth - $head.Length - 6
if ($padding -lt 0 -or $padding -gt 8000) { throw 'Insufficient safe launcher padding.' }
$updated = $head + 'rem ' + ('-' * $padding) + "`r`n" + $body
if ($updated.Length -ne $launcher.Length -or
    $updated.IndexOf($javaLine, [StringComparison]::Ordinal) -ne $javaOffset -or
    $updated.Substring($javaOffset) -cne $launcher.Substring($javaOffset)) {
    throw 'Launcher byte offsets would change. Nothing was staged.'
}
$pending = Join-Path $game "pending-gameplay-$Version"
if (Test-Path -LiteralPath $pending) { throw 'A gameplay hotfix is already staged; inspect it before replacing it.' }
New-Item -ItemType Directory -Path $pending | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $pending "goldclaim-$Version.jar.pending")
Copy-Item -LiteralPath $table -Destination (Join-Path $pending 'monthly.json')
Copy-Item -LiteralPath $collections -Destination (Join-Path $pending 'collections.json')
Copy-Item -LiteralPath $installer -Destination (Join-Path $game 'Install-PendingGameplayHotfix.ps1')
foreach ($entry in @(
    @("goldclaim-$Version.jar.pending", $jarHash),
    @('monthly.json', $tableHash),
    @('collections.json', (Get-FileHash -LiteralPath $collections -Algorithm SHA256).Hash))) {
    if ((Get-FileHash -LiteralPath (Join-Path $pending $entry[0]) -Algorithm SHA256).Hash -ine $entry[1]) {
        throw "Staged hotfix checksum failed: $($entry[0]). The launcher was not changed."
    }
}
$manifest = [ordered]@{
    Version = $Version
    JarSha256 = $jarHash
    MonthlySha256 = $tableHash
    CollectionsSha256 = (Get-FileHash -LiteralPath $collections -Algorithm SHA256).Hash
    PreviousJarSha256 = (Get-FileHash -LiteralPath $previousJar -Algorithm SHA256).Hash
    PreviousMonthlySha256 = (Get-FileHash -LiteralPath $previousTable -Algorithm SHA256).Hash
    PreviousCollectionsSha256 = (Get-FileHash -LiteralPath $previousCollections -Algorithm SHA256).Hash
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $pending 'manifest.json') -Encoding ASCII
Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $game "start.bat.bak_before_goldclaim_$Version")
[IO.File]::WriteAllText($launcherPath, $updated, [Text.Encoding]::ASCII)
Write-Host "Staged GoldClaim $Version for the next normal start. The running server and current End were not changed."
