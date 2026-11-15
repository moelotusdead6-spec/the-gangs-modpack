[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$GravesJar,
    [Parameter(Mandatory)][string]$BackpacksJar,
    [Parameter(Mandatory)][string]$CoreJar
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$compat = Join-Path $repo 'artifacts\gangsgravescompat-1.0.0.jar'
$next = Join-Path $repo 'artifacts\gangs-mount-claim-restart-1.0.1.jar'
$output = Join-Path $repo 'artifacts\gangs-safe-graves-restart-1.0.0.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing launcher: $output" }
if ((Get-FileHash -LiteralPath $next -Algorithm SHA256).Hash -ine
    '7b69f096f8de8fadf2933fe82ed7b71779856affaef52b7ca116de9ed36b8e67') {
    throw 'Selected Mount/Claim launcher does not match the verified live chain.'
}
$build = Join-Path ([IO.Path]::GetTempPath()) ('safe-graves-restart-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'safe-graves-payload\mods'
try {
    New-Item -ItemType Directory -Path $payload -Force | Out-Null
    Copy-Item -LiteralPath $compat -Destination $payload
    & javac --release 17 -cp $next -d $classes (Join-Path $PSScriptRoot 'GravesRestart.java') `
        (Join-Path $PSScriptRoot 'GravesRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Safe-graves launcher compilation failed.' }
    & java -cp "$classes;$next" GravesRestartTest $compat $GravesJar $BackpacksJar $CoreJar
    if ($LASTEXITCODE -ne 0) { throw 'Safe-graves installer tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: GravesRestart`r`n" +
        "Class-Path: fabric-server-launch.jar gangs-mount-claim-restart-1.0.1.jar`r`n`r`n",
        [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes GravesRestart.class `
        -C $classes safe-graves-payload
    if ($LASTEXITCODE -ne 0) { throw 'Safe-graves launcher packaging failed.' }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
