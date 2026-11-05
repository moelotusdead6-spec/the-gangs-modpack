[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$old = Join-Path $repo 'artifacts\alexscaves-2.1.8.jar'
$new = Join-Path $repo 'artifacts\alexscaves-2.1.8-gangs-levelseed.jar'
$oldHash = 'bf0065bbe70b143fafbd7340b1938a99aaefe3ad8236b22ac6aacf277c0b2b4f'
if ((Get-FileHash -LiteralPath $old -Algorithm SHA256).Hash -ine $oldHash) {
    throw "artifacts\alexscaves-2.1.8.jar does not match the live Alex's Caves jar."
}

$build = Join-Path $repo 'tmp\alex-map-seed-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'alexmapseed-payload'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $payload 'mods') | Out-Null
Copy-Item -LiteralPath $new -Destination (Join-Path $payload 'mods\alexscaves-2.1.8-gangs-levelseed.jar')
$newHash = (Get-FileHash -LiteralPath $new -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'),
    [string[]]@("mods/alexscaves-2.1.8.jar=$oldHash", "mods/alexscaves-2.1.8-gangs-levelseed.jar=$newHash"),
    [Text.UTF8Encoding]::new($false))
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'AlexMapSeedRestart.java') (Join-Path $PSScriptRoot 'AlexMapSeedRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Alex map seed restart launcher compilation failed.' }
& java -cp $classes AlexMapSeedRestartTest $old
if ($LASTEXITCODE -ne 0) { throw 'Alex map seed restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
# The chained launchers must be on the class path so their main classes can be invoked.
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: AlexMapSeedRestart`r`n" +
    "Class-Path: fabric-server-launch.jar gangs-alex-boss-restart-1.0.70.jar gangs-passive-spawn-restart-1.0.69.jar`r`n" +
    "  gangs-mimicream-restart-1.0.67.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-alex-map-seed-restart-1.0.71.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes AlexMapSeedRestart.class -C $classes alexmapseed-payload
if ($LASTEXITCODE -ne 0) { throw 'Alex map seed restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
