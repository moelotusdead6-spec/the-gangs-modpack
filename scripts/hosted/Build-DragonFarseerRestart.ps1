[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $repo 'artifacts\gangs-dragon-farseer-restart-1.0.0.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite: $output" }
$build = Join-Path ([IO.Path]::GetTempPath()) ('dragon-farseer-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'dragon-farseer-1.0.0-payload'
$sources = [ordered]@{
    'mods/gangsdragonloot-1.0.0.jar' = 'artifacts\gangsdragonloot-1.0.0.jar'
    'config/alexsmobs/farseer.json' = 'scripts\hosted\farseer-disabled.json'
    'log4j2-server.xml' = 'scripts\hosted\log4j2-dragon-farseer.xml'
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
    & javac --release 17 -d $classes (Join-Path $PSScriptRoot 'DragonFarseerRestart.java') `
        (Join-Path $PSScriptRoot 'DragonFarseerRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    & java -cp $classes DragonFarseerRestartTest $repo
    if ($LASTEXITCODE -ne 0) { throw 'Launcher regressions failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: DragonFarseerRestart`r`n" +
        "Class-Path: fabric-server-launch.jar`r`n" +
        "  gangs-mount-variants-restart-1.0.3.jar`r`n`r`n", [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes DragonFarseerRestart.class `
        -C $classes dragon-farseer-1.0.0-payload
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    & java -cp "$classes;$output" DragonFarseerRestartTest --packaged $output
    if ($LASTEXITCODE -ne 0) {
        Remove-Item -LiteralPath $output -Force
        throw 'Packaged launcher validation failed.'
    }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
