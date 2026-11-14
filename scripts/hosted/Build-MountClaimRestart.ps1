[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$MythicJar,
    [switch]$TestOnly
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$mythic = (Resolve-Path -LiteralPath $MythicJar).Path
$build = Join-Path ([IO.Path]::GetTempPath()) ('mount-claim-restart-' + [Guid]::NewGuid().ToString('N'))
try {
    $classes = Join-Path $build 'classes'
    $payload = Join-Path $classes 'mount-claim-1.0.0-payload'
    New-Item -ItemType Directory -Force -Path $payload | Out-Null
    foreach ($name in @('goldclaim-1.0.75.jar', 'gangshop-1.0.12.jar', 'gangsmounts-1.0.0.jar')) {
        Copy-Item -LiteralPath (Join-Path $repo "artifacts\$name") -Destination $payload
    }
    & javac --release 17 -d $classes (Join-Path $PSScriptRoot 'MountClaimRestart.java') (Join-Path $PSScriptRoot 'MountClaimRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Restart installer compilation failed.' }
    & java -cp $classes MountClaimRestartTest $repo $mythic
    if ($LASTEXITCODE -ne 0) { throw 'Restart installer tests failed.' }
    if (-not $TestOnly) {
        $manifest = Join-Path $build 'MANIFEST.MF'
        [IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: MountClaimRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
        $output = Join-Path $repo 'artifacts\gangs-mount-claim-restart-1.0.1.jar'
        if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
        & jar --create --file $output --manifest $manifest -C $classes MountClaimRestart.class -C $classes mount-claim-1.0.0-payload
        if ($LASTEXITCODE -ne 0) { throw 'Restart installer packaging failed.' }
        Get-FileHash -LiteralPath $output -Algorithm SHA256
    }
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
