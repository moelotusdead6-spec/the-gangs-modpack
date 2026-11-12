[CmdletBinding()]
param(
    [switch]$TestOnly,
    [string]$StagingDirectory,
    [string]$NativeConfig
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
if (-not $StagingDirectory) { $StagingDirectory = Join-Path $repo 'tmp\hub-furniture-smoke-dependencies' }
if (-not $NativeConfig) { $NativeConfig = Join-Path $repo 'tmp\goldclaim-hub-furniture-smoke\config\immersive_furniture.json' }
$build = Join-Path ([IO.Path]::GetTempPath()) ('furniture-hub-restart-' + [Guid]::NewGuid().ToString('N'))
$upstream = [ordered]@{
    'block-pack' = 'bf_blockpack'
    'immersive-furniture' = 'immersive_furniture'
    'urban-decor' = 'urban_decor'
    'just-outdoor-stuffs' = 'justoutdoorstuffs'
    'runiclib' = 'runiclib'
}
$originals = [ordered]@{
    'mods/goldclaim-1.0.73.jar' = '4862b7e859130ff5aaa3fd9fd3cd148cf94472c40e88f8ed6cc1ae30cf6f9b5e'
    'mods/gangshop-1.0.10.jar' = 'd1f0e39b5b98d0256bfa73b0e3e41d960573183b466091fb1145b7c4f762aebb'
}
try {
    $classes = Join-Path $build 'classes'
    $payload = Join-Path $classes 'furniture-hub-1.0.0-payload'
    New-Item -ItemType Directory -Force -Path (Join-Path $payload 'mods'), (Join-Path $payload 'config') | Out-Null
    $lines = [Collections.Generic.List[string]]::new()
    foreach ($entry in $originals.GetEnumerator()) {
        $file = Join-Path $repo ('artifacts\' + [IO.Path]::GetFileName($entry.Key))
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $entry.Value) {
            throw "Original artifact changed: $file"
        }
        $lines.Add("old.$($entry.Key)=$($entry.Value)")
    }
    foreach ($entry in $upstream.GetEnumerator()) {
        $metadata = Get-Content -LiteralPath (Join-Path $repo "mods\$($entry.Key).pw.toml") -Raw
        $filename = [regex]::Match($metadata, '(?m)^filename = "([^"]+)"').Groups[1].Value
        $hash = [regex]::Match($metadata, '(?m)^hash = "([^"]+)"').Groups[1].Value
        if ($metadata -notmatch '(?m)^hash-format = "sha512"' -or -not $filename -or -not $hash) {
            throw "Expected upstream SHA-512 metadata: $($entry.Key)"
        }
        $file = Join-Path $StagingDirectory $filename
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA512).Hash -ine $hash) {
            throw "Staged mod does not match Packwiz: $filename"
        }
        $sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
        $lines.Add("new.mods/$filename=$sha256")
        $lines.Add("staged.mods/$filename=$filename")
        $lines.Add("mod.$($entry.Value)=$filename")
    }
    foreach ($id in @('goldclaim', 'gangshop')) {
        $metadata = Get-Content -LiteralPath (Join-Path $repo "mods\$id-direct.pw.toml") -Raw
        $filename = [regex]::Match($metadata, '(?m)^filename = "([^"]+)"').Groups[1].Value
        $hash = [regex]::Match($metadata, '(?m)^hash = "([^"]+)"').Groups[1].Value
        if ($filename -ne "$id-$(if ($id -eq 'goldclaim') { '1.0.74' } else { '1.0.11' }).jar") {
            throw "Unexpected first-party version: $filename"
        }
        $file = Join-Path $repo "artifacts\$filename"
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $hash) {
            throw "First-party artifact does not match Packwiz: $filename"
        }
        Copy-Item -LiteralPath $file -Destination (Join-Path $payload "mods\$filename")
        $lines.Add("new.mods/$filename=$hash")
        $lines.Add("mod.$id=$filename")
    }
    $config = Join-Path $repo 'config\immersive_furniture.json'
    foreach ($file in @($config, $NativeConfig)) {
        $value = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json
        if ($value.version -ne 2 -or $value.requiredCraftingPermissionLevel -ne 2 -or $value.name -ne 'immersive_furniture') {
            throw "Invalid staff-only furniture configuration: $file"
        }
    }
    Copy-Item -LiteralPath $config -Destination (Join-Path $payload 'config\immersive_furniture.json')
    $lines.Add("new.config/immersive_furniture.json=$((Get-FileHash -LiteralPath $config -Algorithm SHA256).Hash.ToLowerInvariant())")
    $lines.Add("accepted.config/immersive_furniture.json=$((Get-FileHash -LiteralPath $NativeConfig -Algorithm SHA256).Hash.ToLowerInvariant())")
    [IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $lines, [Text.Encoding]::ASCII)
    & javac --release 17 -d $classes (Join-Path $PSScriptRoot 'FurnitureHubRestart.java') `
        (Join-Path $PSScriptRoot 'FurnitureHubRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Furniture/hub restart compilation failed.' }
    & java -cp $classes FurnitureHubRestartTest $repo $StagingDirectory $NativeConfig
    if ($LASTEXITCODE -ne 0) { throw 'Furniture/hub restart tests failed.' }
    if (-not $TestOnly) {
        $manifest = Join-Path $build 'MANIFEST.MF'
        [IO.File]::WriteAllText($manifest,
            "Manifest-Version: 1.0`r`nMain-Class: FurnitureHubRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n",
            [Text.Encoding]::ASCII)
        $output = Join-Path $repo 'artifacts\gangs-furniture-hub-restart-1.0.0.jar'
        if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
        & jar --create --file $output --manifest $manifest -C $classes FurnitureHubRestart.class `
            -C $classes furniture-hub-1.0.0-payload
        if ($LASTEXITCODE -ne 0) { throw 'Furniture/hub restart packaging failed.' }
        Get-FileHash -LiteralPath $output -Algorithm SHA256
    }
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
