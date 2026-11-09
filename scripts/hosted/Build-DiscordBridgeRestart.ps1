[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BridgeJar,
    [switch]$TestOnly
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$jar = (Resolve-Path -LiteralPath $BridgeJar).Path
$next = Join-Path $repo 'artifacts\gangs-chefcompat-restart-1.0.2.jar'
$expectedJar = '8b7c543042b0316f35f224c6437e78a63c8b298eb549fac85965754875f0b00c'
$expectedNext = 'ceb7ff7e49b85c8c19616ba1606f3f1e5e92d398cb2e5d0d846767dfff7c5154'
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ine $expectedJar) {
    throw 'Discord bridge jar does not match the pinned SHA-256.'
}
if ((Get-FileHash -LiteralPath $next -Algorithm SHA256).Hash -ine $expectedNext) {
    throw 'Chef compatibility launcher does not match the pinned SHA-256.'
}
$meta = Get-Content -LiteralPath (Join-Path $repo 'mods\discord-integration.pw.toml') -Raw
if ($meta -notmatch '(?m)^filename = "dcintegration-fabric-3\.0\.7-1\.20\.1\.jar"\r?$' -or
    $meta -notmatch "(?m)^hash = `"$expectedJar`"\r?$") {
    throw 'Discord bridge metadata does not match the verified payload.'
}
$build = Join-Path ([IO.Path]::GetTempPath()) ('discord-bridge-restart-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
try {
    $payload = Join-Path $classes 'discordbridge-payload'
    New-Item -ItemType Directory -Path $payload -Force | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $payload 'mod.jar')
    & javac --release 17 -cp $next -d $classes (Join-Path $PSScriptRoot 'DiscordBridgeRestart.java') `
        (Join-Path $PSScriptRoot 'DiscordBridgeRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Discord bridge launcher compilation failed.' }
    & java -cp "$classes;$next" DiscordBridgeRestartTest $jar
    if ($LASTEXITCODE -ne 0) { throw 'Discord bridge launcher tests failed.' }
    if (-not $TestOnly) {
        $manifest = Join-Path $build 'MANIFEST.MF'
        [IO.File]::WriteAllText($manifest,
            "Manifest-Version: 1.0`r`nMain-Class: DiscordBridgeRestart`r`n" +
            "Class-Path: fabric-server-launch.jar gangs-chefcompat-restart-1.0.2.jar`r`n" +
            "  gangs-goldclaim-mailbox-rswmap-restart-1.0.80.jar`r`n" +
            "  gangs-trials-botania-restart-1.0.74.jar`r`n" +
            "  gangs-oux-badges-restart-1.0.72.jar gangs-alex-map-seed-restart-1.0.71.jar`r`n" +
            "  gangs-alex-boss-restart-1.0.70.jar gangs-passive-spawn-restart-1.0.69.jar`r`n" +
            "  gangs-mimicream-restart-1.0.67.jar`r`n`r`n", [Text.Encoding]::ASCII)
        $output = Join-Path $repo 'artifacts\gangs-discord-bridge-restart-1.0.1.jar'
        if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
        & jar --create --file $output --manifest $manifest -C $classes DiscordBridgeRestart.class `
            -C $classes discordbridge-payload
        if ($LASTEXITCODE -ne 0) { throw 'Discord bridge launcher packaging failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($output)
        try {
            $reader = [IO.StreamReader]::new($archive.GetEntry('META-INF/MANIFEST.MF').Open())
            try { $actual = ($reader.ReadToEnd() -replace "\r?\n ", '') } finally { $reader.Dispose() }
        } finally { $archive.Dispose() }
        $expectedClassPath = @(
            'fabric-server-launch.jar', 'gangs-chefcompat-restart-1.0.2.jar',
            'gangs-goldclaim-mailbox-rswmap-restart-1.0.80.jar',
            'gangs-trials-botania-restart-1.0.74.jar', 'gangs-oux-badges-restart-1.0.72.jar',
            'gangs-alex-map-seed-restart-1.0.71.jar', 'gangs-alex-boss-restart-1.0.70.jar',
            'gangs-passive-spawn-restart-1.0.69.jar', 'gangs-mimicream-restart-1.0.67.jar'
        ) -join ' '
        $actualClassPath = [regex]::Match($actual, '(?m)^Class-Path: ([^\r\n]+)').Groups[1].Value
        if ($actualClassPath -cne $expectedClassPath) {
            Remove-Item -LiteralPath $output -Force
            throw 'Packaged launcher has an invalid Class-Path.'
        }
        Get-FileHash -LiteralPath $output -Algorithm SHA256
    }
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
