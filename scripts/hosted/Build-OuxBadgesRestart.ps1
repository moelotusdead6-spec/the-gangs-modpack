[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$staged = [ordered]@{
    'mods/goldclaim-1.0.64.jar'  = @('artifacts\goldclaim-1.0.64.jar', 'bfed5f9a22681b235d792d6809b4f59c103547a3a6ded5218986b6bde77e7b8b')
    'mods/goldclaim-1.0.65.jar'  = @('artifacts\goldclaim-1.0.65.jar', 'd102e4fe1557ddff6afae3dedb4815f2b136ac70a7d9e620f557aa1f4953d6fa')
    'mods/rankbadges-1.0.3.jar'  = @('artifacts\rankbadges-1.0.3.jar', 'b30a179d167071292ad2dddd93953a92f9dd2378354770826873e6316d87d79f')
}
foreach ($entry in $staged.GetEnumerator()) {
    $file = Join-Path $repo $entry.Value[0]
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $entry.Value[1]) {
        throw "$($entry.Value[0]) does not match the live server jar."
    }
}

$build = Join-Path $repo 'tmp\oux-badges-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'ouxbadges-payload'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $payload 'mods'), (Join-Path $payload 'config') | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'artifacts\goldclaim-1.0.66.jar') -Destination (Join-Path $payload 'mods\goldclaim-1.0.66.jar')
Copy-Item -LiteralPath (Join-Path $repo 'artifacts\oux-badges-hiec7dbm.jar') -Destination (Join-Path $payload 'mods\oux-badges-hiec7dbm.jar')
# Ship the config exactly as Oux Badges writes it (LF), even if Git checked it out with CRLF.
$config = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'oux-badges.json')).Replace("`r`n", "`n")
[IO.File]::WriteAllText((Join-Path $payload 'config\oux-badges.json'), $config, [Text.UTF8Encoding]::new($false))

$lines = [Collections.Generic.List[string]]::new()
foreach ($entry in $staged.GetEnumerator()) { $lines.Add("$($entry.Key)=$($entry.Value[1])") }
foreach ($name in 'mods/goldclaim-1.0.66.jar', 'mods/oux-badges-hiec7dbm.jar', 'config/oux-badges.json') {
    $lines.Add("$name=" + (Get-FileHash -LiteralPath (Join-Path $payload $name) -Algorithm SHA256).Hash.ToLowerInvariant())
}
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $lines.ToArray(), [Text.UTF8Encoding]::new($false))

& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'OuxBadgesRestart.java') (Join-Path $PSScriptRoot 'OuxBadgesRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Oux Badges restart launcher compilation failed.' }
& java -cp $classes OuxBadgesRestartTest (Join-Path $repo 'artifacts\goldclaim-1.0.64.jar') `
    (Join-Path $repo 'artifacts\goldclaim-1.0.65.jar') (Join-Path $repo 'artifacts\rankbadges-1.0.3.jar')
if ($LASTEXITCODE -ne 0) { throw 'Oux Badges restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
# The chained launchers must be on the class path so their main classes can be invoked.
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: OuxBadgesRestart`r`n" +
    "Class-Path: fabric-server-launch.jar gangs-alex-map-seed-restart-1.0.71.jar gangs-alex-boss-restart-1.0.70.jar`r`n" +
    "  gangs-passive-spawn-restart-1.0.69.jar gangs-mimicream-restart-1.0.67.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-oux-badges-restart-1.0.72.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes OuxBadgesRestart.class -C $classes ouxbadges-payload
if ($LASTEXITCODE -ne 0) { throw 'Oux Badges restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
