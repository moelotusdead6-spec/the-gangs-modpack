[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$build = Join-Path $repo 'tmp\gameplay-restart-build'
$classes = Join-Path $build 'classes'
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
$payload = Join-Path $classes 'payload'
New-Item -ItemType Directory -Force -Path $payload | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$files = @(
    @{ Source = 'artifacts\goldclaim-1.0.62.jar'; Target = 'mods/goldclaim-1.0.62.jar' },
    @{ Source = 'tmp\gameplay-smoke-dependencies\bclib-3.0.16.jar'; Target = 'mods/bclib-3.0.16.jar' },
    @{ Source = 'tmp\gameplay-smoke-dependencies\better-end-4.0.12.jar'; Target = 'mods/better-end-4.0.12.jar' },
    @{ Source = 'config\fiw-mods-api\config.json'; Target = 'config/fiw-mods-api/config.json' }
)
$hashes = @()
foreach ($file in $files) {
    $target = Join-Path $payload $file.Target.Replace('/', '\')
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo $file.Source) -Destination $target
    $hashes += "$($file.Target)=$((Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant())"
}
$original = [IO.File]::ReadAllText((Join-Path $repo 'config\fiw-mods-api\config.json'))
$original = [regex]::Replace($original, '"bypass_players": \[[^\]]*\]', '"bypass_players": []')
$original = $original.Replace("`r`n", "`n").Replace("`n", "`r`n")
[IO.File]::WriteAllText((Join-Path $payload 'original-fiw.json'), $original, $utf8)
$previous = @(
    'mods/goldclaim-1.0.61.jar=b1347eaf5c301b687c1eda0d2ec92421e4ac6507d491d06a56c003e80aeb2211',
    'mods/bclib-3.0.14.jar=391fa2a8d7e369b088c66d1f230c3951e1d83f19d60555b2efea5394d8bcfc51',
    'mods/better-end-4.0.11.jar=285feee4a2602ac66f7b4d92f0d972ac872aec513c2b61a8f6f447ca3dc08420',
    "config/fiw-mods-api/config.json=$((Get-FileHash -LiteralPath (Join-Path $payload 'original-fiw.json')).Hash.ToLowerInvariant())"
)
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $hashes, $utf8)
[IO.File]::WriteAllLines((Join-Path $payload 'previous.properties'), $previous, $utf8)
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'GameplayRestart.java') (Join-Path $PSScriptRoot 'GameplayRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Gameplay installer compilation failed.' }
& java -cp $classes GameplayRestartTest $repo
if ($LASTEXITCODE -ne 0) { throw 'Gameplay installer regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: GameplayRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-gameplay-restart-1.0.62.jar'
& jar --create --file $output --manifest $manifest -C $classes GameplayRestart.class -C $classes payload
if ($LASTEXITCODE -ne 0) { throw 'Gameplay installer packaging failed.' }
Get-FileHash -LiteralPath $output -Algorithm SHA256
