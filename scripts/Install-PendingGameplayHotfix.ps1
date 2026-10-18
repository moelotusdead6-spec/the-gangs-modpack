[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$GameDirectory,
    [ValidateSet('1.0.39', '1.0.40', '1.0.41', '1.0.42', '1.0.43')][string]$Version = '1.0.39'
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$previousVersion = switch ($Version) { '1.0.43' { '1.0.42' } '1.0.42' { '1.0.41' } '1.0.41' { '1.0.40' } '1.0.40' { '1.0.39' } default { '1.0.38' } }
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
if (Test-Path -LiteralPath $previousJar) { Remove-Item -LiteralPath $previousJar }
Move-Item -LiteralPath $manifestPath -Destination (Join-Path $pending 'manifest.applied.json') -Force
Write-Host "GoldClaim $Version and both reward configurations installed and verified."
