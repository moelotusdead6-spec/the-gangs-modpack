[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$MountDependenciesDir,
    [Parameter(Mandatory)][string]$GravesDependenciesDir,
    [Parameter(Mandatory)][string]$FabricLauncher
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$mountClaim = Join-Path $repo 'artifacts\gangs-mount-claim-restart-1.0.1.jar'
$graves = Join-Path $repo 'artifacts\gangs-safe-graves-restart-1.0.0.jar'
$output = Join-Path $repo 'artifacts\gangs-mount-variants-restart-1.0.3.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing launcher: $output" }
if ((Get-FileHash -LiteralPath $mountClaim -Algorithm SHA256).Hash -ine
    '7b69f096f8de8fadf2933fe82ed7b71779856affaef52b7ca116de9ed36b8e67') { throw 'Mount/Claim predecessor changed.' }
if ((Get-FileHash -LiteralPath $graves -Algorithm SHA256).Hash -ine
    '111c618cfdb4104a7a214bc736e829c709cf7b67f31cddaabda81c46f763d9eb') { throw 'Safe-graves predecessor changed.' }
$build = Join-Path ([IO.Path]::GetTempPath()) ('mount-variants-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'mount-variants-1.0.2-payload'
try {
    New-Item -ItemType Directory -Path $payload -Force | Out-Null
    foreach ($name in @('gangsmounts-1.0.2.jar', 'goldclaim-1.0.76.jar', 'gangsboots-1.0.8.jar')) {
        Copy-Item -LiteralPath (Join-Path $repo "artifacts\$name") -Destination $payload
    }
    & javac --release 17 -cp "$mountClaim;$graves" -d $classes `
        (Join-Path $PSScriptRoot 'MountVariantsRestart.java') (Join-Path $PSScriptRoot 'MountVariantsRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Mount variants installer compilation failed.' }
    & java -cp "$classes;$mountClaim;$graves" MountVariantsRestartTest $repo $MountDependenciesDir $GravesDependenciesDir $FabricLauncher
    if ($LASTEXITCODE -ne 0) { throw 'Mount variants installer tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    $classPath = 'fabric-server-launch.jar gangs-safe-graves-restart-1.0.0.jar gangs-mount-claim-restart-1.0.1.jar'
    # Manifest continuation lines drop exactly one leading space, so the separator needs a second one.
    [IO.File]::WriteAllText($manifest,
        "Manifest-Version: 1.0`r`nMain-Class: MountVariantsRestart`r`n" +
        "Class-Path: fabric-server-launch.jar gangs-safe-graves-restart-1.0.0.jar`r`n" +
        "  gangs-mount-claim-restart-1.0.1.jar`r`n`r`n", [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes MountVariantsRestart.class `
        -C $classes mount-variants-1.0.2-payload
    if ($LASTEXITCODE -ne 0) { throw 'Mount variants installer packaging failed.' }
    try {
        $check = Join-Path $build 'ManifestCheck.java'
        [IO.File]::WriteAllText($check,
            'public class ManifestCheck { public static void main(String[] a) throws Exception { ' +
            'try (var j = new java.util.jar.JarFile(a[0])) { System.out.print(j.getManifest().getMainAttributes().getValue("Class-Path")); } } }')
        $parsed = & java $check $output
        if ($LASTEXITCODE -ne 0 -or $parsed -cne $classPath) { throw "Launcher Class-Path parsed as '$parsed'." }
        # Launch it as the panel does: both predecessor classes must load before the first missing-file check fails.
        $smoke = Join-Path $build 'smoke'
        New-Item -ItemType Directory -Path $smoke | Out-Null
        Copy-Item -LiteralPath $output, $mountClaim, $graves -Destination $smoke
        Push-Location $smoke
        try { $launch = (& cmd /c "java -jar `"$(Split-Path $output -Leaf)`" 2>&1") -join "`n" } finally { Pop-Location }
        if ($launch -match 'NoClassDefFoundError|ClassNotFoundException' -or
            $launch -notmatch 'unexpected SHA-256: .*fabric-server-launch\.jar') {
            throw "Launcher did not resolve its predecessors under java -jar:`n$launch"
        }
    } catch {
        Remove-Item -LiteralPath $output -Force
        throw
    }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
