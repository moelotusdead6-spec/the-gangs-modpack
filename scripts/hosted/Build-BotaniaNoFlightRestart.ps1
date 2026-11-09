[CmdletBinding()]
param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$mod = Join-Path $repo 'artifacts\gangsbotaniacompat-1.0.1.jar'
$next = Join-Path $repo 'artifacts\gangs-discord-bridge-restart-1.0.1.jar'
$modHash = '8f05304d0976bb90fa9ba51ca58b51ab889083e377fede47bea6c99d3f3f2614'
$nextHash = '4a801c6667e5f5bb132e7b8235482cbc044a78e03822a3e25b9d54fb5dc3fab5'
foreach ($entry in @(@($mod, $modHash), @($next, $nextHash))) {
    if ((Get-FileHash -LiteralPath $entry[0] -Algorithm SHA256).Hash -ine $entry[1]) {
        throw "Unexpected SHA-256: $($entry[0])"
    }
}
$metadata = Get-Content -LiteralPath (Join-Path $repo 'mods\gangsbotaniacompat-direct.pw.toml') -Raw
if ($metadata -notmatch '(?m)^filename = "gangsbotaniacompat-1\.0\.1\.jar"\r?$' -or
        $metadata -notmatch "(?m)^hash = `"$modHash`"\r?$") {
    throw 'Botania compatibility metadata does not match the verified payload.'
}
$build = Join-Path ([IO.Path]::GetTempPath()) ('botania-no-flight-restart-' + [Guid]::NewGuid().ToString('N'))
try {
    $classes = Join-Path $build 'classes'
    $payload = Join-Path $classes 'botania-no-flight-1.0.1-payload'
    New-Item -ItemType Directory -Path $payload -Force | Out-Null
    Copy-Item -LiteralPath $mod -Destination (Join-Path $payload 'mod.jar')
    & javac --release 17 -cp $next -d $classes (Join-Path $PSScriptRoot 'BotaniaNoFlightRestart.java') `
        (Join-Path $PSScriptRoot 'BotaniaNoFlightRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Botania no-flight restart compilation failed.' }
    & java -cp "$classes;$next" BotaniaNoFlightRestartTest $mod `
        (Join-Path $repo 'tmp\modcache\Botania-1.20.1-456-FABRIC.jar') $repo
    if ($LASTEXITCODE -ne 0) { throw 'Botania no-flight restart tests failed.' }
    if (-not $TestOnly) {
        $classPath = @(
            'fabric-server-launch.jar', 'gangs-discord-bridge-restart-1.0.1.jar',
            'gangs-chefcompat-restart-1.0.2.jar', 'gangs-goldclaim-mailbox-rswmap-restart-1.0.80.jar',
            'gangs-trials-botania-restart-1.0.74.jar', 'gangs-oux-badges-restart-1.0.72.jar',
            'gangs-alex-map-seed-restart-1.0.71.jar', 'gangs-alex-boss-restart-1.0.70.jar',
            'gangs-passive-spawn-restart-1.0.69.jar', 'gangs-mimicream-restart-1.0.67.jar'
        ) -join ' '
        $manifest = Join-Path $build 'MANIFEST.MF'
        # jar folds long manifest lines; read the packaged Class-Path back before accepting it.
        [IO.File]::WriteAllText($manifest,
            "Manifest-Version: 1.0`r`nMain-Class: BotaniaNoFlightRestart`r`nClass-Path: $classPath`r`n`r`n",
            [Text.Encoding]::ASCII)
        $output = Join-Path $repo 'artifacts\gangs-botania-no-flight-restart-1.0.1.jar'
        if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
        & jar --create --file $output --manifest $manifest -C $classes BotaniaNoFlightRestart.class `
            -C $classes botania-no-flight-1.0.1-payload
        if ($LASTEXITCODE -ne 0) { throw 'Botania no-flight restart packaging failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($output)
        try {
            $reader = [IO.StreamReader]::new($archive.GetEntry('META-INF/MANIFEST.MF').Open())
            try { $unfolded = $reader.ReadToEnd() -replace "\r?\n ", '' } finally { $reader.Dispose() }
            if ($archive.GetEntry('botania-no-flight-1.0.1-payload/mod.jar').Length -ne (Get-Item -LiteralPath $mod).Length) {
                throw 'Packaged no-flight payload is missing or incomplete.'
            }
        } finally { $archive.Dispose() }
        if ([regex]::Match($unfolded, '(?m)^Class-Path: ([^\r\n]+)').Groups[1].Value -cne $classPath) {
            Remove-Item -LiteralPath $output -Force
            throw 'Packaged launcher has an invalid Class-Path.'
        }
        Get-FileHash -LiteralPath $output -Algorithm SHA256
    }
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
