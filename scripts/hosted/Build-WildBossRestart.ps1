[CmdletBinding()]
param(
    # Folder holding the Trials/Lightness staged jars (bclib 3.0.18, better-end 4.0.14, memoryleakfix, inventoryessentials).
    [Parameter(Mandatory = $true)]
    [string]$CandidatesDirectory
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $repo 'artifacts\gangs-wild-boss-restart-1.0.0.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite: $output" }
$build = Join-Path ([IO.Path]::GetTempPath()) ('wild-boss-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'wild-boss-1.0.78-payload'
$sources = [ordered]@{
    'mods/goldclaim-1.0.78.jar' = 'artifacts\goldclaim-1.0.78.jar'
    'config/skieskits/menu.json' = 'config\skieskits\menu.json'
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
    # The menu.json shipped before this update (exact Git bytes) for the upgrade regression test.
    $previousMenu = Join-Path $build 'previous-menu.json'
    & cmd /c "git -C `"$repo`" show be9c39f:config/skieskits/menu.json > `"$previousMenu`""
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the previous menu.json from Git.' }
    $previous = Join-Path $repo 'artifacts\gangs-trials-lightness-restart-1.1.32.jar'
    $mailbox = Join-Path $repo 'artifacts\gangs-inventory-mailbox-restart-1.0.0.jar'
    $dragon = Join-Path $repo 'artifacts\gangs-dragon-farseer-restart-1.0.0.jar'
    $classpath = "$previous;$mailbox;$dragon"
    & javac --release 17 -cp $classpath -d $classes (Join-Path $PSScriptRoot 'WildBossRestart.java') `
        (Join-Path $PSScriptRoot 'WildBossRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    & java -Xmx1g -cp "$classes;$classpath" WildBossRestartTest $repo $CandidatesDirectory $previousMenu
    if ($LASTEXITCODE -ne 0) { throw 'Launcher regression tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    # Manifest continuation lines need two leading spaces (one is stripped).
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: WildBossRestart`r`n" +
        "Class-Path: fabric-server-launch.jar`r`n  gangs-trials-lightness-restart-1.1.32.jar`r`n`r`n", [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes WildBossRestart.class `
        -C $classes wild-boss-1.0.78-payload
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    & java -cp "$classes;$output;$classpath" WildBossRestartTest --packaged $output
    if ($LASTEXITCODE -ne 0) { throw 'Packaged launcher validation failed.' }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
