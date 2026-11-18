[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $repo 'artifacts\gangs-inventory-mailbox-restart-1.0.0.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite: $output" }
$build = Join-Path ([IO.Path]::GetTempPath()) ('inventory-mailbox-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'inventory-mailbox-1.0.0-payload'
$sources = [ordered]@{
    'mods/goldclaim-1.0.77.jar' = 'artifacts\goldclaim-1.0.77.jar'
    'mods/gangsinvhistory-1.0.0.jar' = 'artifacts\gangsinvhistory-1.0.0.jar'
    'mods/InvView-1.4.12-1.20+.jar' = 'artifacts\InvView-1.4.12-1.20+.jar'
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
    $predecessor = Join-Path $repo 'artifacts\gangs-dragon-farseer-restart-1.0.0.jar'
    & javac --release 17 -cp $predecessor -d $classes (Join-Path $PSScriptRoot 'InventoryMailboxRestart.java') `
        (Join-Path $PSScriptRoot 'InventoryMailboxRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    & java -cp "$classes;$predecessor" InventoryMailboxRestartTest $repo
    if ($LASTEXITCODE -ne 0) { throw 'Launcher regressions failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: InventoryMailboxRestart`r`n" +
        "Class-Path: fabric-server-launch.jar`r`n" +
        "  gangs-dragon-farseer-restart-1.0.0.jar`r`n`r`n", [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes InventoryMailboxRestart.class `
        -C $classes inventory-mailbox-1.0.0-payload
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    & java -cp "$classes;$output;$predecessor" InventoryMailboxRestartTest --packaged $output
    if ($LASTEXITCODE -ne 0) {
        Remove-Item -LiteralPath $output -Force
        throw 'Packaged launcher validation failed.'
    }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
