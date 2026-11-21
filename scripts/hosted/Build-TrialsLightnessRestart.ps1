[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$CandidatesDirectory
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $repo 'artifacts\gangs-trials-lightness-restart-1.1.32.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite: $output" }
$build = Join-Path ([IO.Path]::GetTempPath()) ('trials-lightness-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'trials-lightness-1.1.32-payload'
$sources = [ordered]@{
    'mods/gangsboots-1.0.9.jar' = 'artifacts\gangsboots-1.0.9.jar'
    'mods/gangstrials-1.0.9.jar' = 'artifacts\gangstrials-1.0.9.jar'
}
$pool = 'config/paxi/datapacks/gangs_fixes/data/minecraft/worldgen/template_pool/trial_chambers/spawner/'
foreach ($name in @('all','breeze','melee','ranged','slow_ranged','small_melee')) {
    $sources[$pool + $name + '.json'] = ($pool + $name + '.json').Replace('/', '\')
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
    $previous = Join-Path $repo 'artifacts\gangs-inventory-mailbox-restart-1.0.0.jar'
    $dragon = Join-Path $repo 'artifacts\gangs-dragon-farseer-restart-1.0.0.jar'
    & javac --release 17 -cp "$previous;$dragon" -d $classes (Join-Path $PSScriptRoot 'TrialsLightnessRestart.java') `
        (Join-Path $PSScriptRoot 'TrialsLightnessRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    & java -Xmx1g -cp "$classes;$previous;$dragon" TrialsLightnessRestartTest $repo $CandidatesDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Launcher regression tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: TrialsLightnessRestart`r`n" +
        "Class-Path: fabric-server-launch.jar`r`n  gangs-inventory-mailbox-restart-1.0.0.jar`r`n`r`n", [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes TrialsLightnessRestart.class `
        -C $classes trials-lightness-1.1.32-payload
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    & java -cp "$classes;$output;$previous;$dragon" TrialsLightnessRestartTest --packaged $output
    if ($LASTEXITCODE -ne 0) { throw 'Packaged launcher validation failed.' }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
