[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$old = Join-Path $repo 'artifacts\goldclaim-1.0.64.jar'
$new = Join-Path $repo 'artifacts\goldclaim-1.0.65.jar'
$oldHash = 'bfed5f9a22681b235d792d6809b4f59c103547a3a6ded5218986b6bde77e7b8b'
if ((Get-FileHash -LiteralPath $old -Algorithm SHA256).Hash -ine $oldHash) {
    throw 'artifacts\goldclaim-1.0.64.jar does not match the live GoldClaim jar.'
}

$build = Join-Path $repo 'tmp\alex-boss-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'alexboss-payload'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $payload 'mods') | Out-Null
Copy-Item -LiteralPath $new -Destination (Join-Path $payload 'mods\goldclaim-1.0.65.jar')
$newHash = (Get-FileHash -LiteralPath $new -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'),
    [string[]]@("mods/goldclaim-1.0.64.jar=$oldHash", "mods/goldclaim-1.0.65.jar=$newHash"), [Text.UTF8Encoding]::new($false))
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'AlexBossRestart.java') (Join-Path $PSScriptRoot 'AlexBossRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Alex boss restart launcher compilation failed.' }
& java -cp $classes AlexBossRestartTest $old
if ($LASTEXITCODE -ne 0) { throw 'Alex boss restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
# The chained launchers must be on the class path so their main classes can be invoked.
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: AlexBossRestart`r`n" +
    "Class-Path: fabric-server-launch.jar gangs-passive-spawn-restart-1.0.69.jar gangs-mimicream-restart-1.0.67.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-alex-boss-restart-1.0.70.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes AlexBossRestart.class -C $classes alexboss-payload
if ($LASTEXITCODE -ne 0) { throw 'Alex boss restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
