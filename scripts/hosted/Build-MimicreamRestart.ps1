[CmdletBinding()]
param(
    # Directory with byte-exact live copies: tick.live.mcfunction, overrides.live.json, voicechat.live.jar.
    [Parameter(Mandatory)]
    [string]$LiveFixtures
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$LiveFixtures = (Resolve-Path -LiteralPath $LiveFixtures).Path
$expectedLive = @{
    'tick.live.mcfunction' = '9b4733371247d135ed095c24ee152cd95307bd27765e6ab5601e63e853fa6ed7'
    'voicechat.live.jar' = '314765c54a4967a971836469c8ba0a31ff0718f418d54bb770fef8a97f3f1036'
}
foreach ($name in $expectedLive.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $LiveFixtures $name) -Algorithm SHA256).Hash -ine $expectedLive[$name]) {
        throw "Live fixture does not match the staged live file: $name"
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $LiveFixtures 'overrides.live.json') -PathType Leaf)) {
    throw 'Missing live GangShop overrides fixture.'
}

$data = 'config/paxi/datapacks/gangs_fixes/data/'
$tick = $data + 'gangs_fixes/functions/tick.mcfunction'
$targets = @(
    ($data + 'alexsmobs/loot_tables/entities/mimicube.json'),
    ($data + 'alexsmobs/loot_tables/gameplay/transmutation_table_rare.json'),
    ($data + 'alexsmobs/recipes/hemolymph_blaster.json'),
    ($data + 'alexsmobs/recipes/mimicream_repair_recipe.json'),
    ($data + 'gobber2/tags/items/ring_repair_blacklist.json'),
    $tick
)
$deny = (Get-Content -LiteralPath (Join-Path $repo 'config\gangshop\overrides.json') -Raw | ConvertFrom-Json).denyItems.'alexsmobs:mimicream'
if ($deny -ne $true) { throw 'Repository shop overrides do not deny alexsmobs:mimicream.' }

$build = Join-Path $repo 'tmp\mimicream-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'payload'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $payload | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$hashes = foreach ($target in $targets) {
    $source = Join-Path $repo $target.Replace('/', '\')
    if ([IO.File]::ReadAllText($source).Contains("`r")) { throw "Payload must use LF line endings: $target" }
    $destination = Join-Path $payload $target.Replace('/', '\')
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($destination)) | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination
    "$target=$((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant())"
}
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), [string[]]$hashes, $utf8)
[IO.File]::WriteAllLines((Join-Path $payload 'previous.properties'), [string[]]@("$tick=$($expectedLive['tick.live.mcfunction'])"), $utf8)
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'MimicreamRestart.java') (Join-Path $PSScriptRoot 'MimicreamRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Mimicream restart launcher compilation failed.' }
& java -cp $classes MimicreamRestartTest $LiveFixtures
if ($LASTEXITCODE -ne 0) { throw 'Mimicream restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: MimicreamRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-mimicream-restart-1.0.67.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes MimicreamRestart.class -C $classes payload
if ($LASTEXITCODE -ne 0) { throw 'Mimicream restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
