[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
# Live files replaced or removed by the launcher, with the hashes read from the server before staging.
$old = [ordered]@{
    'mods/gangshop-1.0.9.jar'        = '3100529cb5468548c2cf13d30b67aca9590a8b9ed257f89ce987ffcda54e9a9b'
    'config/lootr.json'              = '82f2dd46d9ed748579640c215511de9484c4d158e354ccdbd6b4f08fdf82052b'
    'config/gangshop/overrides.json' = '51d05ab2cccdec7784d3f30862487bc7938f97fdb3308a1e90cbff323d2c8d29'
    # The live server still runs the pre-v1.1.19 build, so the 1.0.71 map-seed launcher refused to swap it.
    'mods/alexscaves-2.1.8.jar'      = 'f748286c86f361e9ed05f5c70846d18ddd01f77ee4abfed588e45164173ba766'
}
# The level-seed jar is read from the map-seed launcher already on the server instead of being embedded again.
$mapSeedLauncher = Join-Path $repo 'artifacts\gangs-alex-map-seed-restart-1.0.71.jar'
if ((Get-FileHash -LiteralPath $mapSeedLauncher -Algorithm SHA256).Hash -ine 'b42f1dd8616d1b992ded7f5d399e586f8bc443edbe8f6547a84c9a27266b87b3') {
    throw 'artifacts\gangs-alex-map-seed-restart-1.0.71.jar does not match the live launcher.'
}
$levelSeedHash = 'ec1606e1efdf43f635cf0e74a559a229e133a9f90f85c64119a9027ff421ceeb'
if ((Get-Content -LiteralPath (Join-Path $repo 'mods\alexscaves-direct.pw.toml') -Raw) -notmatch $levelSeedHash) {
    throw 'mods\alexscaves-direct.pw.toml no longer pins the level-seed jar.'
}
if ((Get-FileHash -LiteralPath (Join-Path $repo 'artifacts\gangshop-1.0.9.jar') -Algorithm SHA256).Hash -ine $old['mods/gangshop-1.0.9.jar']) {
    throw 'artifacts\gangshop-1.0.9.jar does not match the live server jar.'
}
# The regression test needs the live originals, downloaded from the server into tmp\live.
foreach ($name in 'lootr.json', 'overrides.json', 'alexscaves-2.1.8.jar') {
    $key = @{ 'lootr.json' = 'config/lootr.json'; 'overrides.json' = 'config/gangshop/overrides.json'
        'alexscaves-2.1.8.jar' = 'mods/alexscaves-2.1.8.jar' }[$name]
    $copy = Join-Path $repo "tmp\live\$name"
    if (-not (Test-Path -LiteralPath $copy) -or (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash -ine $old[$key]) {
        throw "tmp\live\$name must be the live server's $key."
    }
}

$build = Join-Path $repo 'tmp\trials-botania-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'trialsbotania-payload'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $payload | Out-Null

function Add-Payload([string]$Name, [string]$Source) {
    $target = Join-Path $payload $Name
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $target
}

# Server jars come from the pack metadata, verified against the pack's own hashes.
foreach ($toml in 'botania', 'backport-copper-age', 'sherdsapi', 'trimmed', 'tricky-trials-backport-direct',
        'gangstrials-direct', 'gangshop-direct') {
    $meta = Get-Content -LiteralPath (Join-Path $repo "mods\$toml.pw.toml") -Raw
    $file = [regex]::Match($meta, '(?m)^filename = "(.+)"').Groups[1].Value
    $url = [regex]::Match($meta, '(?m)^url = "(.+)"').Groups[1].Value
    $format = [regex]::Match($meta, '(?m)^hash-format = "(.+)"').Groups[1].Value
    $expected = [regex]::Match($meta, '(?m)^hash = "(.+)"').Groups[1].Value
    $local = Join-Path $repo "artifacts\$file"
    $download = Join-Path $build $file
    if (Test-Path -LiteralPath $local) { Copy-Item -LiteralPath $local -Destination $download }
    else { Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $download }
    if ((Get-FileHash -LiteralPath $download -Algorithm $format.ToUpperInvariant()).Hash -ine $expected) {
        throw "$file does not match mods\$toml.pw.toml."
    }
    Add-Payload "mods/$file" $download
}
Add-Payload 'config/gangstrials.json' (Join-Path $repo 'config\gangstrials.json')
Add-Payload 'config/lootr.json' (Join-Path $repo 'config\lootr.json')
# The live overrides plus the trial/backport deny entries; the live file differs from the repo copy.
Add-Payload 'config/gangshop/overrides.json' (Join-Path $PSScriptRoot 'trials-botania-overrides.json')
$datapack = 'config/paxi/datapacks/gangs_fixes/data/minecraft'
foreach ($name in 'tags/blocks/wither_immune.json', 'tags/blocks/dragon_immune.json',
        'worldgen/structure/trial_chambers.json',
        'worldgen/template_pool/trial_chambers/spawner/contents/melee.json',
        'worldgen/template_pool/trial_chambers/spawner/contents/ranged.json',
        'worldgen/template_pool/trial_chambers/spawner/contents/slow_ranged.json',
        'worldgen/template_pool/trial_chambers/spawner/contents/small_melee.json') {
    Add-Payload "$datapack/$name" (Join-Path $repo ("$datapack/$name" -replace '/', '\'))
}

$lines = [Collections.Generic.List[string]]::new()
foreach ($entry in $old.GetEnumerator()) { $lines.Add("old.$($entry.Key)=$($entry.Value)") }
foreach ($file in Get-ChildItem -LiteralPath $payload -Recurse -File) {
    $name = $file.FullName.Substring($payload.Length + 1).Replace('\', '/')
    $lines.Add("new.$name=" + (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant())
}
$lines.Add("new.mods/alexscaves-2.1.8-gangs-levelseed.jar=$levelSeedHash")
$lines.Add('src.mods/alexscaves-2.1.8-gangs-levelseed.jar=/alexmapseed-payload/mods/alexscaves-2.1.8-gangs-levelseed.jar')
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $lines.ToArray(), [Text.UTF8Encoding]::new($false))

& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'TrialsBotaniaRestart.java') (Join-Path $PSScriptRoot 'TrialsBotaniaRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Trials/Botania restart launcher compilation failed.' }
& java -cp "$classes;$mapSeedLauncher" TrialsBotaniaRestartTest (Join-Path $repo 'artifacts\gangshop-1.0.9.jar') (Join-Path $repo 'tmp\live')
if ($LASTEXITCODE -ne 0) { throw 'Trials/Botania restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
# The chained launchers must be on the class path so their main classes can be invoked.
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: TrialsBotaniaRestart`r`n" +
    "Class-Path: fabric-server-launch.jar gangs-oux-badges-restart-1.0.72.jar gangs-alex-map-seed-restart-1.0.71.jar`r`n" +
    "  gangs-alex-boss-restart-1.0.70.jar gangs-passive-spawn-restart-1.0.69.jar gangs-mimicream-restart-1.0.67.jar`r`n`r`n",
    [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-trials-botania-restart-1.0.74.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes TrialsBotaniaRestart.class -C $classes trialsbotania-payload
if ($LASTEXITCODE -ne 0) { throw 'Trials/Botania restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
