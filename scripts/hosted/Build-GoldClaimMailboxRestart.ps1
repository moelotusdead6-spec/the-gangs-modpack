[CmdletBinding()]
param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$old = [ordered]@{
    'mods/goldclaim-1.0.68.jar' = '3c3c59e1f528a4fc699732190551ada24c9b05515a73ed92f05d4651a127f6ee'
    'mods/goldclaim-1.0.69.jar' = '91b84e88fcfb8cabe5aef855b584c529cd81bd6e75bb0dc4a9430637f988e8f7'
    'mods/goldclaim-1.0.70.jar' = '381022a2cf10c6a5de6de7368158e5181d24744833259a5f4764e022301a6352'
}
$newFile = 'goldclaim-1.0.71.jar'
$newHash = '3c1af3abe852511f2fd6a432959528c16383fbec077467c5d5a8617d7caa2697'
$next = Join-Path $repo 'artifacts\gangs-trials-botania-restart-1.0.74.jar'
if ((Get-FileHash -LiteralPath $next -Algorithm SHA256).Hash -ine 'bdac9539a5c8b0f4917ff97b56b39af349bf3549873fe8a69f54f81eba646c48') {
    throw 'The existing downstream restart launcher does not match its staged checksum.'
}
$meta = Get-Content -LiteralPath (Join-Path $repo 'mods\goldclaim-direct.pw.toml') -Raw
$file = [regex]::Match($meta, '(?m)^filename = "(.+)"').Groups[1].Value
$expected = [regex]::Match($meta, '(?m)^hash = "(.+)"').Groups[1].Value
if ($file -ne $newFile -or $expected -ine $newHash) { throw 'GoldClaim metadata is not pinned to the verified 1.0.71 jar.' }
$jar = Join-Path $repo "artifacts\$file"
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ine $expected) { throw "$file does not match its toml." }
foreach ($entry in $old.GetEnumerator()) {
    $local = Join-Path $repo ("artifacts\" + ($entry.Key -replace '^mods/', ''))
    if ((Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash -ine $entry.Value) {
        throw "$local does not match its expected original checksum."
    }
}

$build = Join-Path $repo 'tmp\goldclaim-mailbox-rswmap-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'mailbox-rswmap-payload\mods'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $payload | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $payload $file)

& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'GoldClaimMailboxRestart.java') `
    (Join-Path $PSScriptRoot 'GoldClaimMailboxRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'GoldClaim mailbox restart launcher compilation failed.' }
& java -cp $classes GoldClaimMailboxRestartTest $jar `
    (Join-Path $repo 'artifacts\goldclaim-1.0.68.jar') `
    (Join-Path $repo 'artifacts\goldclaim-1.0.69.jar') `
    (Join-Path $repo 'artifacts\goldclaim-1.0.70.jar')
if ($LASTEXITCODE -ne 0) { throw 'GoldClaim mailbox restart regression tests failed.' }
if ($TestOnly) {
    Remove-Item -LiteralPath $build -Recurse -Force
    return
}

$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest,
    "Manifest-Version: 1.0`r`nMain-Class: GoldClaimMailboxRestart`r`n" +
    "Class-Path: fabric-server-launch.jar gangs-trials-botania-restart-1.0.74.jar gangs-oux-badges-restart-1.0.72.jar`r`n" +
    "  gangs-alex-map-seed-restart-1.0.71.jar gangs-alex-boss-restart-1.0.70.jar gangs-passive-spawn-restart-1.0.69.jar`r`n" +
    "  gangs-mimicream-restart-1.0.67.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-goldclaim-mailbox-rswmap-restart-1.0.78.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes GoldClaimMailboxRestart.class `
    -C $classes mailbox-rswmap-payload
if ($LASTEXITCODE -ne 0) { throw 'GoldClaim mailbox restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
