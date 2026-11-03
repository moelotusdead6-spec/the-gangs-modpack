[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$build = Join-Path $repo 'tmp\hosted-next-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'payload'
New-Item -ItemType Directory -Force -Path $payload | Out-Null
$pause = Join-Path $repo 'artifacts\gangs-performance-pause-1.0.0.zip'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packRoot = Join-Path $repo 'config\paxi\datapacks\gangs_performance_pause'
$stream = [IO.File]::Create($pause)
$zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $packRoot -File -Recurse | Sort-Object FullName) {
        $relative = $file.FullName.Substring($packRoot.Length + 1).Replace('\', '/')
        $entry = $zip.CreateEntry($relative, [IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime = [DateTimeOffset]::new(2026, 10, 22, 0, 0, 0, [TimeSpan]::Zero)
        $target = $entry.Open()
        try {
            $source = [IO.File]::OpenRead($file.FullName)
            try { $source.CopyTo($target) } finally { $source.Dispose() }
        } finally { $target.Dispose() }
    }
} finally {
    $zip.Dispose()
    $stream.Dispose()
}
Copy-Item -LiteralPath $pause -Destination (Join-Path $payload 'gangs-performance-pause-1.0.0.zip') -Force
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'NextRestart.java')
if ($LASTEXITCODE -ne 0) { throw 'Next-start launcher compilation failed.' }
foreach ($name in @('goldclaim-1.0.49.jar', 'gangsales-1.0.1.jar')) {
    Copy-Item -LiteralPath (Join-Path $repo "artifacts\$name") -Destination (Join-Path $payload $name) -Force
}
Copy-Item -LiteralPath (Join-Path $repo 'config\gangshop\overrides.json') -Destination (Join-Path $payload 'gangshop-overrides.json') -Force
$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: NextRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path $repo 'artifacts\gangs-next-restart-1.0.49.jar'
& jar --create --file $output --manifest $manifest -C $classes NextRestart.class -C $classes 'NextRestart$Update.class' -C $classes payload\goldclaim-1.0.49.jar -C $classes payload\gangsales-1.0.1.jar -C $classes payload\gangshop-overrides.json -C $classes payload\gangs-performance-pause-1.0.0.zip
if ($LASTEXITCODE -ne 0) { throw 'Next-start launcher packaging failed.' }
Get-FileHash -LiteralPath $output -Algorithm SHA256
