$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $repo 'artifacts\gangs-gobber-shop-restart-1.0.79-r2.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite: $output" }
$build = Join-Path ([IO.Path]::GetTempPath()) ('gobber-shop-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'gobber-shop-1.0.79-payload'
$sources = [ordered]@{
    'mods/goldclaim-1.0.79.jar' = 'artifacts\goldclaim-1.0.79.jar'
    'mods/gangshop-1.0.14.jar' = 'artifacts\gangshop-1.0.14.jar'
    'config/paxi/datapacks/gangs_fixes/data/c/tags/items/hidden_from_recipe_viewers.json' =
        'config\paxi\datapacks\gangs_fixes\data\c\tags\items\hidden_from_recipe_viewers.json'
}
try {
    $checksums = @()
    foreach ($entry in $sources.GetEnumerator()) {
        $target = Join-Path $payload ($entry.Key.Replace('/', '\'))
        New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $repo $entry.Value) -Destination $target
        $checksums += $entry.Key + '=' + (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [IO.File]::WriteAllLines((Join-Path $payload 'checksums.properties'), $checksums, [Text.Encoding]::ASCII)
    & javac --release 17 -d $classes (Join-Path $PSScriptRoot 'GobberShopRestart.java') `
        (Join-Path $PSScriptRoot 'GobberShopRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    & java -cp $classes GobberShopRestartTest $repo (Join-Path $build 'fixture')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher regression tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: GobberShopRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n",
        [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes GobberShopRestart.class `
        -C $classes gobber-shop-1.0.79-payload
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    & java -cp "$output;$classes" GobberShopRestartTest --packaged
    if ($LASTEXITCODE -ne 0) { throw 'Packaged launcher validation failed.' }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
