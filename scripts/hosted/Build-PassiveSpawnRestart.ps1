[CmdletBinding()]
param(
    # Directory containing byte-exact live WildlifeConfig.json and wildernature.json.
    [Parameter(Mandatory)]
    [string]$LiveFixtures
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$LiveFixtures = (Resolve-Path -LiteralPath $LiveFixtures).Path
$expected = @{
    'WildlifeConfig.json' = '8e082f291718541a5f2b9973683926e0701ac9757b05c1157c2017ac9135814e'
    'wildernature.json' = 'ebef10b2bbbc025766fb34b1cd31c46f3fbc3d35454144aa8e2a3dca3f33a01b'
}
foreach ($name in $expected.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $LiveFixtures $name) -Algorithm SHA256).Hash -ine $expected[$name]) {
        throw "Live fixture does not match the staged live file: $name"
    }
}
$wildlife = Get-Content -LiteralPath (Join-Path $repo 'config\WildlifeConfig.json') -Raw | ConvertFrom-Json
if ($wildlife.globalSettings.configVersion -ne 5 -or @($wildlife.mobConfig.PSObject.Properties).Count -ne 74) {
    throw 'Wildlife configuration is missing required version-5 settings.'
}
foreach ($entry in $wildlife.mobConfig.PSObject.Properties) {
    if ($null -eq $entry.Value.spawnBiomes -or -not @($entry.Value.spawnBiomes).Count -or
        -not $entry.Value.spawnCategory) {
        throw "Missing Wildlife habitat or spawn category: $($entry.Name)"
    }
}
foreach ($name in @('HAMSTER', 'CAPYBARA')) {
    $category = $wildlife.spawnCategories.($wildlife.mobConfig.$name.spawnCategory)
    if (-not $category.shouldSpawn -or $category.spawnWeight -ne 12 -or
        $category.spawnRandomChance -ne 100 -or $category.chunkFreq -ne 1 -or
        $category.minSpawnGroupSize -ne 3 -or $category.maxSpawnGroupSize -ne 5) {
        throw "Unexpected boosted spawn settings: $name"
    }
}
function Assert-WildlifeHabitat([string]$Name, [string]$Biome) {
    $habitats = foreach ($entry in $wildlife.mobConfig.$Name.spawnBiomes) {
        $group = $wildlife.biomeGroups.PSObject.Properties[$entry]
        if ($null -eq $group) { $entry } else { $group.Value }
    }
    if (@($habitats) -notcontains $Biome) { throw "Missing $Name habitat: $Biome" }
}
Assert-WildlifeHabitat 'HAMSTER' 'minecraft:desert'
Assert-WildlifeHabitat 'HAMSTER' 'regions_unexplored:outback'
Assert-WildlifeHabitat 'CAPYBARA' 'minecraft:jungle'
Assert-WildlifeHabitat 'CAPYBARA' 'minecraft:bamboo_jungle'
Assert-WildlifeHabitat 'MONKEY' 'terralith:tropical_jungle'
Assert-WildlifeHabitat 'COTTONBIRD' 'minecraft:snowy_taiga'
Assert-WildlifeHabitat 'CHICKADEE' 'minecraft:snowy_taiga'
$chickadeeGroups = @($wildlife.mobConfig.CHICKADEE.spawnBiomes)
if ($chickadeeGroups -notcontains 'forests') { throw 'Chickadees have no forest habitat.' }
$wildernature = Get-Content -LiteralPath (Join-Path $repo 'config\wildernature.json') -Raw | ConvertFrom-Json
foreach ($name in @('removeSavannaAnimals', 'removeSwampAnimals', 'removeJungleAnimals', 'removeForestAnimals')) {
    if ($wildernature.$name -ne $false) { throw "Vanilla animal removal is still enabled: $name" }
}

$output = Join-Path $repo 'artifacts\gangs-passive-spawn-restart-1.0.69.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
$chained = Join-Path $repo 'artifacts\gangs-mimicream-restart-1.0.67.jar'
if ((Get-FileHash -LiteralPath $chained -Algorithm SHA256).Hash -ine
    '95cc10a794dc7d4300dce62b561f23f6482b8c6a9fd0690dd0dca3966c33afd6') {
    throw 'The pending Mimicream launcher does not match the live launcher.'
}
$build = Join-Path ([IO.Path]::GetTempPath()) ("passive-spawn-restart-" + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'passive-spawn-payload'
try {
    New-Item -ItemType Directory -Force -Path (Join-Path $payload 'config') | Out-Null
    $hashes = foreach ($name in @('WildlifeConfig.json', 'wildernature.json')) {
        $source = Join-Path $repo "config\$name"
        $bytes = [IO.File]::ReadAllBytes($source)
        if (($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191) -or
            [IO.File]::ReadAllText($source).Contains("`r")) {
            throw "Payload must use LF without a BOM: $name"
        }
        $destination = Join-Path $payload "config\$name"
        Copy-Item -LiteralPath $source -Destination $destination
        "config/$name=$((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant())"
    }
    [IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), [string[]]$hashes,
        [Text.UTF8Encoding]::new($false))
    & javac --release 17 -cp $chained -d $classes (Join-Path $PSScriptRoot 'PassiveSpawnRestart.java') `
        (Join-Path $PSScriptRoot 'PassiveSpawnRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Passive spawn restart launcher compilation failed.' }
    & java -cp ($classes + [IO.Path]::PathSeparator + $chained) PassiveSpawnRestartTest $LiveFixtures
    if ($LASTEXITCODE -ne 0) { throw 'Passive spawn restart regression tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: PassiveSpawnRestart`r`n" +
        "Class-Path: fabric-server-launch.jar gangs-mimicream-restart-1.0.67.jar`r`n`r`n",
        [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes PassiveSpawnRestart.class `
        -C $classes passive-spawn-payload
    if ($LASTEXITCODE -ne 0) { throw 'Passive spawn restart launcher packaging failed.' }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
