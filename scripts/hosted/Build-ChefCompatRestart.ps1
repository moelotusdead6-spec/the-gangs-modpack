[CmdletBinding()]
param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$compat = Join-Path $repo 'artifacts\gangschefcompat-1.0.0.jar'
$chef = Join-Path $repo 'tmp\modcache\chefs-delight-1.0.5-fa-1.20.1.jar'
$farmer = Join-Path $repo 'tmp\modcache\FarmersDelight-1.20.1-2.5.7+refabricated.jar'
$next = Join-Path $repo 'artifacts\gangs-goldclaim-mailbox-rswmap-restart-1.0.80.jar'
$goldclaim = Join-Path $repo 'artifacts\goldclaim-1.0.72.jar'
$expected = [ordered]@{
    $compat = '1fab7ab76a13e545ca73156ffa1ab7bded6bd93478c9cbef5b8d98707047c5a9'
    $chef = 'f13cf8e835af96055c4ff962ad6505aa6229e68759e1d4c70e71ab0bbf5dedf3'
    $farmer = '7f5c6cf0e05f66c11925c6e1e66c513fa4f9660a742e38762c929b3ca37de2d9'
    $next = '2ef28ca884025351918d61aac961cb3e9515694b6ee1b69c057dfd895596b673'
    $goldclaim = '7a656631ad8bf19eff7cc74154e128de30dc79b7f0eb743992f00ffc9ef14ae5'
}
foreach ($entry in $expected.GetEnumerator()) {
    if ((Get-FileHash -LiteralPath $entry.Key -Algorithm SHA256).Hash -ine $entry.Value) {
        throw "Unexpected SHA-256: $($entry.Key)"
    }
}
$meta = Get-Content -LiteralPath (Join-Path $repo 'mods\gangschefcompat-direct.pw.toml') -Raw
if ($meta -notmatch '(?m)^filename = "gangschefcompat-1\.0\.0\.jar"\r?$' -or
    $meta -notmatch '(?m)^hash = "1fab7ab76a13e545ca73156ffa1ab7bded6bd93478c9cbef5b8d98707047c5a9"\r?$') {
    throw 'Compatibility metadata does not match the verified payload.'
}

$build = Join-Path ([IO.Path]::GetTempPath()) ('chefcompat-restart-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'chefcompat-payload\mods'
try {
    New-Item -ItemType Directory -Path $payload -Force | Out-Null
    Copy-Item -LiteralPath $compat -Destination (Join-Path $payload 'gangschefcompat-1.0.0.jar')
    & javac --release 17 -cp $next -d $classes (Join-Path $PSScriptRoot 'ChefCompatRestart.java') `
        (Join-Path $PSScriptRoot 'ChefCompatRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Chef compatibility launcher compilation failed.' }
    & java -cp "$classes;$next" ChefCompatRestartTest $compat $chef $farmer $goldclaim
    if ($LASTEXITCODE -ne 0) { throw 'Chef compatibility launcher tests failed.' }
    if (-not $TestOnly) {
        $manifest = Join-Path $build 'MANIFEST.MF'
        [IO.File]::WriteAllText($manifest,
            "Manifest-Version: 1.0`r`nMain-Class: ChefCompatRestart`r`n" +
            "Class-Path: fabric-server-launch.jar gangs-goldclaim-mailbox-rswmap-restart-1.0.80.jar`r`n" +
            "  gangs-trials-botania-restart-1.0.74.jar`r`n" +
            "  gangs-oux-badges-restart-1.0.72.jar gangs-alex-map-seed-restart-1.0.71.jar`r`n" +
            "  gangs-alex-boss-restart-1.0.70.jar gangs-passive-spawn-restart-1.0.69.jar`r`n" +
            "  gangs-mimicream-restart-1.0.67.jar`r`n`r`n", [Text.Encoding]::ASCII)
        $output = Join-Path $repo 'artifacts\gangs-chefcompat-restart-1.0.2.jar'
        if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
        & jar --create --file $output --manifest $manifest -C $classes ChefCompatRestart.class `
            -C $classes chefcompat-payload
        if ($LASTEXITCODE -ne 0) { throw 'Chef compatibility launcher packaging failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::OpenRead($output)
        try {
            $reader = [IO.StreamReader]::new($archive.GetEntry('META-INF/MANIFEST.MF').Open())
            try { $packagedManifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
        } finally { $archive.Dispose() }
        $unfolded = $packagedManifest -replace "\r?\n ", ''
        $actualClassPath = [regex]::Match($unfolded, '(?m)^Class-Path: ([^\r\n]+)').Groups[1].Value
        $expectedClassPath = @(
            'fabric-server-launch.jar',
            'gangs-goldclaim-mailbox-rswmap-restart-1.0.80.jar',
            'gangs-trials-botania-restart-1.0.74.jar',
            'gangs-oux-badges-restart-1.0.72.jar',
            'gangs-alex-map-seed-restart-1.0.71.jar',
            'gangs-alex-boss-restart-1.0.70.jar',
            'gangs-passive-spawn-restart-1.0.69.jar',
            'gangs-mimicream-restart-1.0.67.jar'
        ) -join ' '
        if ($actualClassPath -cne $expectedClassPath) {
            Remove-Item -LiteralPath $output -Force
            throw 'Packaged launcher has an invalid Class-Path.'
        }
        Get-FileHash -LiteralPath $output -Algorithm SHA256
    }
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
