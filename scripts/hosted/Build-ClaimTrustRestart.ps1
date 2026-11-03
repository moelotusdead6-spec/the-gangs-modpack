[CmdletBinding()]
param(
    # Directory with byte-exact copies of the live files the launcher replaces or merges.
    [Parameter(Mandatory)]
    [string]$LiveFixtures
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$LiveFixtures = (Resolve-Path -LiteralPath $LiveFixtures).Path
$expectedLive = @{
    'goldclaim-1.0.63.live.jar' = '7d66559036d14812a71fed22443554861b992bd7efbcf574f07cd8dd5671b49e'
    'neon-v8.live.jar' = 'a0b36248530bfcbc398167b8cb7cade47d494053167822f33d2abe17d42158cc'
    'neon_signs.live.json' = '65e946b23a275a1ccb63135a405e04eb20ba4cf92b507370dec4f6eaf3aced11'
    'fabric-server-launch.live.jar' = '6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028'
}
foreach ($name in $expectedLive.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $LiveFixtures $name) -Algorithm SHA256).Hash -ine $expectedLive[$name]) {
        throw "Live fixture does not match the staged live file: $name"
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $LiveFixtures 'overrides.live.json') -PathType Leaf)) {
    throw 'Missing live GangShop overrides fixture.'
}

$tag = 'config\paxi\datapacks\gangs_kits\data\gangs\tags\items\neon_signs.json'
$files = @(
    @{ Source = 'artifacts\goldclaim-1.0.64.jar'; Target = 'mods/goldclaim-1.0.64.jar'; Hash = 'bfed5f9a22681b235d792d6809b4f59c103547a3a6ded5218986b6bde77e7b8b' },
    @{ Source = 'artifacts\neon-wall-wonders-gwbzt3mk.jar'; Target = 'mods/neon-wall-wonders-gwbzt3mk.jar'; Hash = '0c9fcc4a8d8468b477f11214efdf8220bfd3e3402b7d56478caeda84efc26f29' },
    @{ Source = $tag; Target = $tag.Replace('\', '/'); Hash = $null }
)
$build = Join-Path $repo 'tmp\claim-trust-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'payload'
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $payload | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$hashes = @()
foreach ($file in $files) {
    $target = Join-Path $payload $file.Target.Replace('/', '\')
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo $file.Source) -Destination $target
    $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($file.Hash -and $hash -ne $file.Hash) { throw "Unexpected payload hash: $($file.Source)" }
    $hashes += "$($file.Target)=$hash"
}
$previous = @(
    "mods/goldclaim-1.0.63.jar=$($expectedLive['goldclaim-1.0.63.live.jar'])",
    "mods/neon-wall-wonders-gwbzt3mk\ (10).jar=$($expectedLive['neon-v8.live.jar'])",
    "$($tag.Replace('\', '/'))=$($expectedLive['neon_signs.live.json'])"
)
$overrides = Get-Content -LiteralPath (Join-Path $repo 'config\gangshop\overrides.json') -Raw | ConvertFrom-Json
$deny = @($overrides.denyItems.PSObject.Properties | Where-Object { $_.Name -like 'neon-wall-wonders-gwbzt3mk:*' -and $_.Value -eq $true } | ForEach-Object Name)
$tagIds = @((Get-Content -LiteralPath (Join-Path $repo $tag) -Raw | ConvertFrom-Json).values)
foreach ($id in $tagIds) { if ($deny -notcontains $id) { throw "Neon kit item is not shop-denied: $id" } }
if ($tagIds.Count -ne 87 -or $deny.Count -ne 97) { throw "Unexpected Neon counts: tag $($tagIds.Count), deny $($deny.Count)" }

[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $hashes, $utf8)
[IO.File]::WriteAllLines((Join-Path $payload 'previous.properties'), $previous, $utf8)
[IO.File]::WriteAllText((Join-Path $payload 'deny.txt'), (($deny | Sort-Object) -join "`n") + "`n", $utf8)
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'ClaimTrustRestart.java') (Join-Path $PSScriptRoot 'ClaimTrustRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Claim trust restart launcher compilation failed.' }
& java -cp $classes ClaimTrustRestartTest $LiveFixtures
if ($LASTEXITCODE -ne 0) { throw 'Claim trust restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: ClaimTrustRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-claim-trust-restart-1.0.65.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
& jar --create --file $output --manifest $manifest -C $classes ClaimTrustRestart.class -C $classes payload
if ($LASTEXITCODE -ne 0) { throw 'Claim trust restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
