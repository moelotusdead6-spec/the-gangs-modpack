[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$classes = Join-Path $repo 'tmp\claim-lunar-restart-build\classes'
$payload = Join-Path $classes 'payload'
New-Item -ItemType Directory -Force -Path $payload | Out-Null
$entries = [ordered]@{
    'goldclaim-1.0.51.jar' = 'artifacts\goldclaim-1.0.51.jar'
    'goldclaim-1.0.52.jar' = 'artifacts\goldclaim-1.0.52.jar'
    'fabric-server-launch.jar' = 'tmp\rsw-biome-prod-tests\fabric-server-launch.jar'
    'mods/supplementaries-1.20-3.1.44-fabric.jar' = 'artifacts\supplementaries-1.20-3.1.44-fabric.jar'
    'mods/Enhanced-Celestials-fabric-1.20.1-5.0.3.1.jar' = 'tmp\enhanced-celestials-release-check.jar'
}
$data = 'config\paxi\datapacks\gangs_fixes\data'
foreach ($relative in @(
    'multiworld\enhancedcelestials\lunar\dimension_settings\rsw.json',
    'enhancedcelestials\enhancedcelestials\lunar\event\blood_moon.json',
    'enhancedcelestials\enhancedcelestials\lunar\event\super_blood_moon.json'
)) {
    $path = "$data\$relative"
    $entries[$path.Replace('\', '/')] = $path
}
$events = @('blue_moon.json', 'super_blue_moon.json', 'harvest_moon.json', 'super_harvest_moon.json', 'super_moon.json')
foreach ($event in $events) {
    $entries[$event] = "$data\enhancedcelestials\enhancedcelestials\lunar\event\$event"
}
$lines = foreach ($entry in $entries.GetEnumerator()) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $repo $entry.Value) -Algorithm SHA256).Hash.ToLowerInvariant()
    "$($entry.Key)=$hash"
}
[IO.File]::WriteAllLines((Join-Path $payload 'hashes.properties'), [string[]]$lines, [Text.Encoding]::ASCII)
Copy-Item -LiteralPath (Join-Path $repo 'artifacts\goldclaim-1.0.52.jar') -Destination $payload -Force
foreach ($event in $events) {
    Copy-Item -LiteralPath (Join-Path $repo $entries[$event]) -Destination (Join-Path $payload $event) -Force
}
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'ClaimLunarRestart.java')
if ($LASTEXITCODE -ne 0) { throw 'Claim/lunar restart launcher compilation failed.' }
$manifest = Join-Path $repo 'tmp\claim-lunar-restart-build\MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: ClaimLunarRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-claim-lunar-restart-1.0.52.jar'
& jar --create --file $output --manifest $manifest -C $classes ClaimLunarRestart.class -C $classes payload\hashes.properties -C $classes payload\goldclaim-1.0.52.jar -C $classes payload\blue_moon.json -C $classes payload\super_blue_moon.json -C $classes payload\harvest_moon.json -C $classes payload\super_harvest_moon.json -C $classes payload\super_moon.json
if ($LASTEXITCODE -ne 0) { throw 'Claim/lunar restart launcher packaging failed.' }
Get-FileHash -LiteralPath $output -Algorithm SHA256
