param(
    [string]$Output = (Join-Path $PSScriptRoot '..\artifacts\The-Gangs-Modpack-Installer.zip'),
    [string]$BootstrapJar
)
# Builds a slim Prism instance with no bundled mods. The pre-launch Packwiz hook installs and
# tracks every file on first launch, so new installs always match the live pack.
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
. (Join-Path $PSScriptRoot 'Get-PrismAutoUpdateConfig.ps1')

$pack = Get-Content (Join-Path $root 'pack.toml') -Raw
$fabric = [regex]::Match($pack, '(?m)^fabric = "([^"]+)"').Groups[1].Value
$minecraft = [regex]::Match($pack, '(?m)^minecraft = "([^"]+)"').Groups[1].Value
if (-not $fabric -or -not $minecraft) { throw 'pack.toml is missing Fabric or Minecraft versions.' }

$stage = Join-Path ([IO.Path]::GetTempPath()) ('gangs-installer-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $stage -Force | Out-Null
try {
    if ($BootstrapJar) {
        Copy-Item $BootstrapJar (Join-Path $stage 'packwiz-installer-bootstrap.jar')
    } else {
        Invoke-WebRequest -UseBasicParsing -OutFile (Join-Path $stage 'packwiz-installer-bootstrap.jar') `
            'https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar'
    }

    $cfg = Get-PrismAutoUpdateConfig ("[General]`r`nConfigVersion=1.2`r`niconKey=default`r`n" +
        "name=The Gangs Modpack`r`nnotes=Managed by Packwiz. Mods download automatically on every launch. Do not add mods manually.`r`n" +
        "`r`n[Java]`r`nOverrideJavaLocation=false`r`n")
    [IO.File]::WriteAllText((Join-Path $stage 'instance.cfg'), $cfg)

    $mmc = [ordered]@{
        components = @(
            [ordered]@{ cachedName = 'LWJGL 3'; cachedVersion = '3.3.1'; cachedVolatile = $true; dependencyOnly = $true; uid = 'org.lwjgl3'; version = '3.3.1' },
            [ordered]@{ cachedName = 'Minecraft'; cachedVersion = $minecraft; important = $true; uid = 'net.minecraft'; version = $minecraft },
            [ordered]@{ cachedName = 'Fabric Loader'; cachedVersion = $fabric; uid = 'net.fabricmc.fabric-loader'; version = $fabric }
        )
        formatVersion = 1
    }
    [IO.File]::WriteAllText((Join-Path $stage 'mmc-pack.json'), ($mmc | ConvertTo-Json -Depth 5))

    if (Test-Path $Output) { Remove-Item $Output }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    # Explicit entries avoid the backslash names Windows PowerShell's CreateFromDirectory writes.
    $zip = [IO.Compression.ZipFile]::Open([IO.Path]::GetFullPath($Output), 'Create')
    try {
        foreach ($name in 'instance.cfg', 'mmc-pack.json', 'packwiz-installer-bootstrap.jar') {
            [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $stage $name), $name)
        }
    } finally { $zip.Dispose() }
    Write-Host "Built $Output (Minecraft $minecraft, Fabric $fabric)"
} finally {
    Remove-Item $stage -Recurse -Force
}
