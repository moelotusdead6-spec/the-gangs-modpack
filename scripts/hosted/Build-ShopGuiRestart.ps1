[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$CandidatesDirectory
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $repo 'artifacts\gangs-shop-gui-restart-1.0.13.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite: $output" }
$build = Join-Path ([IO.Path]::GetTempPath()) ('shop-gui-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'shop-gui-1.0.13-payload'
$sources = [ordered]@{
    'mods/gangshop-1.0.13.jar' = 'artifacts\gangshop-1.0.13.jar'
    'config/gangshop/access.json' = 'config\gangshop\access.json'
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
    $wildboss = Join-Path $repo 'artifacts\gangs-wild-boss-restart-1.0.0.jar'
    $trials = Join-Path $repo 'artifacts\gangs-trials-lightness-restart-1.1.32.jar'
    $mailbox = Join-Path $repo 'artifacts\gangs-inventory-mailbox-restart-1.0.0.jar'
    $dragon = Join-Path $repo 'artifacts\gangs-dragon-farseer-restart-1.0.0.jar'
    $classpath = "$wildboss;$trials;$mailbox;$dragon"
    & javac --release 17 -cp $classpath -d $classes (Join-Path $PSScriptRoot 'WildBossRestartTest.java') `
        (Join-Path $PSScriptRoot 'ShopGuiRestart.java') (Join-Path $PSScriptRoot 'ShopGuiRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    & java -Xmx1g -cp "$classes;$classpath" ShopGuiRestartTest $repo $CandidatesDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Launcher regression tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: ShopGuiRestart`r`n" +
        "Class-Path: fabric-server-launch.jar`r`n  gangs-wild-boss-restart-1.0.0.jar`r`n`r`n", [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes ShopGuiRestart.class `
        -C $classes shop-gui-1.0.13-payload
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    & java -cp "$classes;$output;$classpath" ShopGuiRestartTest --packaged $output
    if ($LASTEXITCODE -ne 0) { throw 'Packaged launcher validation failed.' }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
