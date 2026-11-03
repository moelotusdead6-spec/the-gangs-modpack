[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$GameDirectory,
    [ValidateSet('1.0.39', '1.0.40', '1.0.41', '1.0.42', '1.0.43', '1.0.44')][string]$Version = '1.0.44'
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$previousVersion = switch ($Version) { '1.0.44' { '1.0.43' } '1.0.43' { '1.0.42' } '1.0.42' { '1.0.41' } '1.0.41' { '1.0.40' } '1.0.40' { '1.0.39' } default { '1.0.38' } }
$jar = Join-Path $repo "artifacts\goldclaim-$Version.jar"
$table = Join-Path $repo 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'
$collections = Join-Path $repo 'config\randomcollections\config.json'
$previousJar = Join-Path $game "mods\goldclaim-$previousVersion.jar"
$previousTable = Join-Path $game 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'
$previousCollections = Join-Path $game 'config\randomcollections\config.json'
$installer = Join-Path $PSScriptRoot 'Install-PendingGameplayHotfix.ps1'
$launcherPath = Join-Path $game 'start.bat'
$rankExtras = @()
$oldRanks = @()
if ($Version -eq '1.0.44') {
    $chunky = Join-Path $game 'mods\Chunky-1.3.146.jar'
    if (-not (Test-Path -LiteralPath $chunky -PathType Leaf) -or
        (Get-FileHash -LiteralPath $chunky -Algorithm SHA256).Hash -ine
        'ae7fb9d35a3a9d9d4f2104aeae7b31a901c5e5842891e2edd9ddcc42c2646a38') {
        throw 'GoldClaim 1.0.44 requires the pinned Chunky 1.3.146 jar already installed. No files were staged.'
    }
    $rankExtras = @(
        @{ Source = 'artifacts\rankbadges-1.0.3.jar'; Pending = 'rankbadges-1.0.3.jar.pending'; Target = 'mods\rankbadges-1.0.3.jar' },
        @{ Source = 'config\paxi\datapacks\gangs_hub\data\gangs_hub\functions\setup_ranks.mcfunction'; Pending = 'setup_ranks.mcfunction'; Target = 'config\paxi\datapacks\gangs_hub\data\gangs_hub\functions\setup_ranks.mcfunction' },
        @{ Source = 'config\paxi\datapacks\gangs_hub\data\gangs_hub\functions\assign_ranks.mcfunction'; Pending = 'assign_ranks.mcfunction'; Target = 'config\paxi\datapacks\gangs_hub\data\gangs_hub\functions\assign_ranks.mcfunction' }
    )
    foreach ($entry in $rankExtras) {
        if (-not (Test-Path -LiteralPath (Join-Path $repo $entry.Source) -PathType Leaf)) { throw "Rank hotfix source missing: $($entry.Source)" }
        $entry.Sha256 = (Get-FileHash -LiteralPath (Join-Path $repo $entry.Source)).Hash
        $target = Join-Path $game $entry.Target
        $entry.PreviousSha256 = if (Test-Path -LiteralPath $target) { (Get-FileHash -LiteralPath $target).Hash } else { $null }
    }
    $oldRanks = @(Get-ChildItem -LiteralPath (Join-Path $game 'mods') -Filter 'rankbadges-*.jar')
    if (@($oldRanks | Where-Object { $_.Name -ne 'rankbadges-1.0.2.jar' }).Count) {
        throw 'Expected only Rank Badges 1.0.2 (or none) before staging 1.0.3.'
    }
}
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
foreach ($entry in $rankExtras) {
    Copy-Item -LiteralPath (Join-Path $repo $entry.Source) -Destination (Join-Path $pending $entry.Pending)
    if ((Get-FileHash -LiteralPath (Join-Path $pending $entry.Pending)).Hash -ine $entry.Sha256) {
        throw "Staged rank checksum failed: $($entry.Pending). The launcher was not changed."
    }
}
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
    RankExtras = $rankExtras
    PreviousRankBadgesSha256 = if ($oldRanks.Count) { (Get-FileHash -LiteralPath $oldRanks[0].FullName).Hash } else { $null }
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $pending 'manifest.json') -Encoding ASCII
Copy-Item -LiteralPath $launcherPath -Destination (Join-Path $game "start.bat.bak_before_goldclaim_$Version")
[IO.File]::WriteAllText($launcherPath, $updated, [Text.Encoding]::ASCII)
Write-Host "Staged GoldClaim $Version for the next normal start. The running server and current End were not changed."
