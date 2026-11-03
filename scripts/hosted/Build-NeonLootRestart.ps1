[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$goldclaimMetadata = Get-Content -LiteralPath (Join-Path $repo 'mods\goldclaim-direct.pw.toml') -Raw
$neonMetadata = Get-Content -LiteralPath (Join-Path $repo 'mods\neon-wall-wonders.pw.toml') -Raw
if ($goldclaimMetadata -notmatch '(?m)^filename\s*=\s*"goldclaim-1\.0\.63\.jar"\s*$' -or
        $neonMetadata -notmatch '(?m)^filename\s*=\s*"neon-wall-wonders-gwbzt3mk \(10\)\.jar"\s*$') {
    throw 'Historical Neon loot launcher targets GoldClaim 1.0.63 and Neon Wall Wonders 8; it is incompatible with the current pack.'
}
$jarSource = Join-Path $repo 'sources\goldclaim\build\libs\goldclaim-1.0.63.jar'
if (-not (Test-Path -LiteralPath $jarSource -PathType Leaf) -or
        (Get-FileHash -LiteralPath $jarSource -Algorithm SHA256).Hash -ine '7d66559036d14812a71fed22443554861b992bd7efbcf574f07cd8dd5671b49e') {
    throw 'The historical GoldClaim 1.0.63 build is missing or does not match the installed launcher payload.'
}
$build = Join-Path $repo 'tmp\neon-loot-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'payload'
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $payload | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$files = @(
    @{ Source = $jarSource; Target = 'mods/goldclaim-1.0.63.jar' },
    @{ Source = 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\weekly_1.json'; Target = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_1.json' },
    @{ Source = 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\weekly_2.json'; Target = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_2.json' },
    @{ Source = 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\weekly_3.json'; Target = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_3.json' },
    @{ Source = 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'; Target = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/monthly.json' },
    @{ Source = 'config\paxi\datapacks\gangs_kits\data\gangs\tags\items\neon_signs.json'; Target = 'config/paxi/datapacks/gangs_kits/data/gangs/tags/items/neon_signs.json' }
)
$hashes = @()
foreach ($file in $files) {
    $target = Join-Path $payload $file.Target.Replace('/', '\')
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
    $source = if ([IO.Path]::IsPathRooted($file.Source)) { $file.Source } else { Join-Path $repo $file.Source }
    Copy-Item -LiteralPath $source -Destination $target
    $hashes += "$($file.Target)=$((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant())"
}
$previous = @(
    'mods/goldclaim-1.0.62.jar=adb06111a1cdcde6961a8e062bd03a74fd9117d6c00d43bacfc0e344952042f9',
    'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_1.json=a6003cc9e926f9d980dac5419e034e5d47910e636e1f18b56832d59e6563f4a5',
    'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_2.json=a6003cc9e926f9d980dac5419e034e5d47910e636e1f18b56832d59e6563f4a5',
    'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_3.json=a6003cc9e926f9d980dac5419e034e5d47910e636e1f18b56832d59e6563f4a5',
    'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/monthly.json=fa180d607d29fdebcf8691b14512ec1ba2121b75b6240e1664689f97f796ac8c',
    'config/paxi/datapacks/gangs_kits/data/gangs/tags/items/neon_signs.json=ABSENT'
)
$originals = @(
    @{ Git = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_1.json'; Target = $files[1].Target; Hash = 'a6003cc9e926f9d980dac5419e034e5d47910e636e1f18b56832d59e6563f4a5'; LineEnding = 'CRLF' },
    @{ Git = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_2.json'; Target = $files[2].Target; Hash = 'a6003cc9e926f9d980dac5419e034e5d47910e636e1f18b56832d59e6563f4a5'; LineEnding = 'CRLF' },
    @{ Git = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/weekly_3.json'; Target = $files[3].Target; Hash = 'a6003cc9e926f9d980dac5419e034e5d47910e636e1f18b56832d59e6563f4a5'; LineEnding = 'CRLF' },
    @{ Git = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/kits/monthly.json'; Target = $files[4].Target; Hash = 'fa180d607d29fdebcf8691b14512ec1ba2121b75b6240e1664689f97f796ac8c'; LineEnding = 'LF' }
)
foreach ($original in $originals) {
    $temporary = Join-Path $build ('original-' + [IO.Path]::GetFileName($original.Target))
    & $env:ComSpec /d /c "git show HEAD:$($original.Git) > `"$temporary`""
    if ($LASTEXITCODE -ne 0) { throw "Cannot read committed baseline: $($original.Git)" }
    $text = [IO.File]::ReadAllText($temporary).TrimStart([char]0xFEFF)
    $text = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    if ($original.LineEnding -eq 'CRLF') { $text = $text.Replace("`n", "`r`n") }
    $target = Join-Path $payload ('original\' + $original.Target.Replace('/', '\'))
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
    [IO.File]::WriteAllText($target, $text, $utf8)
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ine $original.Hash) {
        throw "Committed baseline does not match the live file hash: $($original.Target)"
    }
}
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $hashes, $utf8)
[IO.File]::WriteAllLines((Join-Path $payload 'previous.properties'), $previous, $utf8)
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'NeonLootRestart.java') (Join-Path $PSScriptRoot 'NeonLootRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Neon loot restart launcher compilation failed.' }
$liveMonthly = Join-Path $repo 'tmp\live-check\monthly.live.json'
if (Test-Path -LiteralPath $liveMonthly) { $env:NEON_LIVE_MONTHLY = $liveMonthly }
& java -cp $classes NeonLootRestartTest $repo
if ($LASTEXITCODE -ne 0) { throw 'Neon loot restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: NeonLootRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-neon-loot-restart-1.0.64.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes NeonLootRestart.class -C $classes payload
if ($LASTEXITCODE -ne 0) { throw 'Neon loot restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
