[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
# The live GoldClaim jar, hash read from the server before staging.
$old = [ordered]@{ 'mods/goldclaim-1.0.68.jar' = '3c3c59e1f528a4fc699732190551ada24c9b05515a73ed92f05d4651a127f6ee' }
$liveJar = Join-Path $repo 'artifacts\goldclaim-1.0.68.jar'
if ((Get-FileHash -LiteralPath $liveJar -Algorithm SHA256).Hash -ine $old['mods/goldclaim-1.0.68.jar']) {
    throw 'artifacts\goldclaim-1.0.68.jar does not match the live server jar.'
}
$next = Join-Path $repo 'artifacts\gangs-end-portal-restart-1.0.75.jar'
if ((Get-FileHash -LiteralPath $next -Algorithm SHA256).Hash -ine 'fe69d4d021c41cc4219be53301f99f3f5e2e90b5db5bc748090786243cabc341') {
    throw 'artifacts\gangs-end-portal-restart-1.0.75.jar does not match the live launcher.'
}
$meta = Get-Content -LiteralPath (Join-Path $repo 'mods\goldclaim-direct.pw.toml') -Raw
$file = [regex]::Match($meta, '(?m)^filename = "(.+)"').Groups[1].Value
$expected = [regex]::Match($meta, '(?m)^hash = "(.+)"').Groups[1].Value
if ($file -ne 'goldclaim-1.0.69.jar') { throw "mods\goldclaim-direct.pw.toml pins $file, not goldclaim-1.0.69.jar." }
$jar = Join-Path $repo "artifacts\$file"
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ine $expected) { throw "$file does not match its toml." }

$build = Join-Path $repo 'tmp\end-portal2-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'endportal2-payload'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $payload 'mods') | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $payload "mods\$file")

$lines = [Collections.Generic.List[string]]::new()
foreach ($entry in $old.GetEnumerator()) { $lines.Add("old.$($entry.Key)=$($entry.Value)") }
$lines.Add("new.mods/$file=$($expected.ToLowerInvariant())")
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $lines.ToArray(), [Text.UTF8Encoding]::new($false))

& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'EndPortal2Restart.java') (Join-Path $PSScriptRoot 'EndPortal2RestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'End portal restart launcher compilation failed.' }
& java -cp $classes EndPortal2RestartTest $liveJar
if ($LASTEXITCODE -ne 0) { throw 'End portal restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
# The chained launchers must be on the class path so their main classes can be invoked.
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: EndPortal2Restart`r`n" +
    "Class-Path: fabric-server-launch.jar gangs-end-portal-restart-1.0.75.jar gangs-trials-botania-restart-1.0.74.jar gangs-oux-badges-restart-1.0.72.jar`r`n" +
    "  gangs-alex-map-seed-restart-1.0.71.jar gangs-alex-boss-restart-1.0.70.jar gangs-passive-spawn-restart-1.0.69.jar`r`n" +
    "  gangs-mimicream-restart-1.0.67.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-end-portal-restart-1.0.76.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes EndPortal2Restart.class -C $classes endportal2-payload
if ($LASTEXITCODE -ne 0) { throw 'End portal restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
