param(
    [Parameter(Mandatory = $true)][string]$FabricLauncher,
    [Parameter(Mandatory = $true)][string]$FurnitureJar,
    [Parameter(Mandatory = $true)][string]$DiscordJar
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$output = Join-Path $repo 'artifacts\gangs-commands-restart-1.0.0.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite: $output" }
$upgrade = Join-Path $repo 'artifacts\gangs-gems-upgrade-0.1.2.jar'
$gobber = Join-Path $repo 'artifacts\gangs-gobber-shop-restart-1.0.79-r2.jar'
$build = Join-Path ([IO.Path]::GetTempPath()) ('gangs-commands-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'commands-1.0.0-payload'
$sources = [ordered]@{
    'goldclaim-1.0.80.jar' = 'sources\goldclaim\build\libs\goldclaim-1.0.80.jar'
    'gangsgems-0.1.3.jar' = 'sources\gangsgems\build\libs\gangsgems-0.1.3.jar'
    'gangsmounts-1.0.3.jar' = 'sources\gangsmounts\build\libs\gangsmounts-1.0.3.jar'
}
try {
    New-Item -ItemType Directory -Path $payload -Force | Out-Null
    $checksums = @()
    foreach ($entry in $sources.GetEnumerator()) {
        $target = Join-Path $payload $entry.Key
        Copy-Item -LiteralPath (Join-Path $repo $entry.Value) -Destination $target
        $checksums += $entry.Key + '=' + (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [IO.File]::WriteAllLines((Join-Path $payload 'checksums.properties'), $checksums, [Text.Encoding]::ASCII)
    & javac --release 17 -cp "$upgrade;$gobber" -d $classes `
        (Join-Path $PSScriptRoot 'CommandsRestart.java') (Join-Path $PSScriptRoot 'CommandsRestartTest.java') `
        (Join-Path $PSScriptRoot 'GobberShopRestartTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
    & java -cp "$classes;$upgrade;$gobber" CommandsRestartTest $repo (Join-Path $build 'fixture') $FabricLauncher $FurnitureJar $DiscordJar
    if ($LASTEXITCODE -ne 0) { throw 'Launcher regression tests failed.' }
    $manifest = Join-Path $build 'MANIFEST.MF'
    [IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: CommandsRestart`r`n" +
        "Class-Path: fabric-server-launch.jar`r`n  gangs-gems-upgrade-0.1.2.jar`r`n" +
        "  gangs-gobber-shop-restart-1.0.79-r2.jar`r`n`r`n", [Text.Encoding]::ASCII)
    & jar --create --file $output --manifest $manifest -C $classes CommandsRestart.class `
        -C $classes commands-1.0.0-payload
    if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed.' }
    & java -cp "$output;$classes;$upgrade;$gobber" CommandsRestartTest $repo (Join-Path $build 'packaged-fixture') $FabricLauncher $FurnitureJar $DiscordJar
    if ($LASTEXITCODE -ne 0) { throw 'Packaged launcher validation failed.' }
    $fixture = Join-Path $build 'packaged-fixture'
    Copy-Item -LiteralPath $output -Destination (Join-Path $fixture ([IO.Path]::GetFileName($output)))
    Push-Location $fixture
    try {
        $ErrorActionPreference = 'Continue'
        $result = & java -jar (Join-Path $fixture ([IO.Path]::GetFileName($output))) 2>&1 | Out-String
        $ErrorActionPreference = 'Stop'
        if ($LASTEXITCODE -eq 0 -or $result -notmatch 'gangsmounts-1\.0\.3\.jar') {
            throw "Packaged launcher preflight did not reject the corrupt installed mod: $result"
        }
    } finally {
        $ErrorActionPreference = 'Stop'
        Pop-Location
    }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
} finally {
    if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
}
