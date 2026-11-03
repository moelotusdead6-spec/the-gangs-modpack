[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$GameDirectory,
    [ValidateSet('1.0.39', '1.0.40', '1.0.41', '1.0.42', '1.0.43', '1.0.44')][string]$Version = '1.0.39'
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$previousVersion = switch ($Version) { '1.0.44' { '1.0.43' } '1.0.43' { '1.0.42' } '1.0.42' { '1.0.41' } '1.0.41' { '1.0.40' } '1.0.40' { '1.0.39' } default { '1.0.38' } }
$pending = Join-Path $game "pending-gameplay-$Version"
$manifestPath = Join-Path $pending 'manifest.json'
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { return }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.Version -cne $Version) { throw 'Unexpected pending gameplay hotfix version.' }
$jar = Join-Path $pending "goldclaim-$Version.jar.pending"
$table = Join-Path $pending 'monthly.json'
$collections = Join-Path $pending 'collections.json'
$installedJar = Join-Path $game "mods\goldclaim-$Version.jar"
$previousJar = Join-Path $game "mods\goldclaim-$previousVersion.jar"
$installedTable = Join-Path $game 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'
$installedCollections = Join-Path $game 'config\randomcollections\config.json'
$backup = Join-Path $game "mod-backups\gameplay-$Version"

function Assert-Hash([string]$Path, [string]$Expected) {
    if (-not $Expected -or -not (Test-Path -LiteralPath $Path -PathType Leaf) -or
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ine $Expected) {
        throw "Hotfix checksum failed: $Path"
    }
}

Assert-Hash $jar $manifest.JarSha256
Assert-Hash $table $manifest.MonthlySha256
Assert-Hash $collections $manifest.CollectionsSha256
if ($Version -eq '1.0.44') {
    Assert-Hash (Join-Path $game 'mods\Chunky-1.3.146.jar') 'ae7fb9d35a3a9d9d4f2104aeae7b31a901c5e5842891e2edd9ddcc42c2646a38'
    $expectedRanks = @{
        'rankbadges-1.0.3.jar.pending' = 'mods\rankbadges-1.0.3.jar'
        'setup_ranks.mcfunction' = 'config\paxi\datapacks\gangs_hub\data\gangs_hub\functions\setup_ranks.mcfunction'
        'assign_ranks.mcfunction' = 'config\paxi\datapacks\gangs_hub\data\gangs_hub\functions\assign_ranks.mcfunction'
    }
    if (@($manifest.RankExtras).Count -ne 3) { throw 'Rank payload is incomplete.' }
    $seen = @{}
    foreach ($entry in $manifest.RankExtras) {
        if ($expectedRanks[$entry.Pending] -cne $entry.Target -or $seen.ContainsKey($entry.Pending)) {
            throw 'Unexpected or duplicate rank payload path.'
        }
        $seen[$entry.Pending] = $true
        Assert-Hash (Join-Path $pending $entry.Pending) $entry.Sha256
        $target = Join-Path $game $entry.Target
        if (Test-Path -LiteralPath $target) {
            $hash = (Get-FileHash -LiteralPath $target).Hash
            if ($hash -ine $entry.PreviousSha256 -and $hash -ine $entry.Sha256) { throw "Rank file changed since staging: $target" }
        } elseif ($entry.PreviousSha256) { throw "Rank file removed since staging: $target" }
    }
    foreach ($rankJar in Get-ChildItem -LiteralPath (Join-Path $game 'mods') -Filter 'rankbadges-*.jar') {
        if ($rankJar.Name -eq 'rankbadges-1.0.2.jar') {
            Assert-Hash $rankJar.FullName $manifest.PreviousRankBadgesSha256
        } elseif ($rankJar.Name -ne 'rankbadges-1.0.3.jar') { throw 'Unexpected Rank Badges version.' }
    }
}
$currentTableHash = (Get-FileHash -LiteralPath $installedTable -Algorithm SHA256).Hash
if ($currentTableHash -ine $manifest.PreviousMonthlySha256 -and $currentTableHash -ine $manifest.MonthlySha256) {
    throw 'The live monthly kit table changed after staging. Restage instead of overwriting it.'
}
$currentCollectionsHash = (Get-FileHash -LiteralPath $installedCollections -Algorithm SHA256).Hash
if ($currentCollectionsHash -ine $manifest.PreviousCollectionsSha256 -and $currentCollectionsHash -ine $manifest.CollectionsSha256) {
    throw 'The live monthly reward collections changed after staging. Restage instead of overwriting them.'
}
foreach ($other in Get-ChildItem -LiteralPath (Join-Path $game 'mods') -Filter 'goldclaim-*.jar') {
    if ($other.Name -notin @("goldclaim-$previousVersion.jar", "goldclaim-$Version.jar")) {
        throw "Unexpected GoldClaim version; install aborted: $($other.Name)"
    }
}
New-Item -ItemType Directory -Path $backup -Force | Out-Null
if ($Version -eq '1.0.44') {
    foreach ($entry in $manifest.RankExtras) {
        $target = Join-Path $game $entry.Target
        if ((Test-Path -LiteralPath $target) -and $entry.PreviousSha256) {
            $saved = Join-Path $backup $entry.Pending
            if (-not (Test-Path -LiteralPath $saved)) { Copy-Item -LiteralPath $target -Destination $saved }
            Assert-Hash $saved $entry.PreviousSha256
        }
    }
    $oldRank = Join-Path $game 'mods\rankbadges-1.0.2.jar'
    if (Test-Path -LiteralPath $oldRank) {
        $saved = Join-Path $backup 'rankbadges-1.0.2.jar'
        if (-not (Test-Path -LiteralPath $saved)) { Copy-Item -LiteralPath $oldRank -Destination $saved }
        Assert-Hash $saved $manifest.PreviousRankBadgesSha256
    }
}
if (Test-Path -LiteralPath $previousJar -PathType Leaf) {
    Assert-Hash $previousJar $manifest.PreviousJarSha256
    $savedJar = Join-Path $backup "goldclaim-$previousVersion.jar"
    if (-not (Test-Path -LiteralPath $savedJar)) { Copy-Item -LiteralPath $previousJar -Destination $savedJar }
    Assert-Hash $savedJar $manifest.PreviousJarSha256
} else {
    Assert-Hash $installedJar $manifest.JarSha256
}
if ($currentTableHash -ieq $manifest.PreviousMonthlySha256) {
    $savedTable = Join-Path $backup 'monthly.json'
    if (-not (Test-Path -LiteralPath $savedTable)) { Copy-Item -LiteralPath $installedTable -Destination $savedTable }
    Assert-Hash $savedTable $manifest.PreviousMonthlySha256
}
if ($currentCollectionsHash -ieq $manifest.PreviousCollectionsSha256) {
    $savedCollections = Join-Path $backup 'collections.json'
    if (-not (Test-Path -LiteralPath $savedCollections)) { Copy-Item -LiteralPath $installedCollections -Destination $savedCollections }
    Assert-Hash $savedCollections $manifest.PreviousCollectionsSha256
}
Copy-Item -LiteralPath $jar -Destination $installedJar -Force
Assert-Hash $installedJar $manifest.JarSha256
Copy-Item -LiteralPath $table -Destination $installedTable -Force
Assert-Hash $installedTable $manifest.MonthlySha256
Copy-Item -LiteralPath $collections -Destination $installedCollections -Force
Assert-Hash $installedCollections $manifest.CollectionsSha256
if ($Version -eq '1.0.44') {
    foreach ($entry in $manifest.RankExtras) {
        $target = Join-Path $game $entry.Target
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $pending $entry.Pending) -Destination $target -Force
        Assert-Hash $target $entry.Sha256
    }
    if (Test-Path -LiteralPath $oldRank) { Remove-Item -LiteralPath $oldRank }
}
if (Test-Path -LiteralPath $previousJar) { Remove-Item -LiteralPath $previousJar }
Move-Item -LiteralPath $manifestPath -Destination (Join-Path $pending 'manifest.applied.json') -Force
Write-Host "GoldClaim $Version and both reward configurations installed and verified."
