[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseZip
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$root = Join-Path $repo ('tmp\auto-update-test-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
$published = Join-Path $root 'published'
$game = Join-Path $root 'client'
New-Item -ItemType Directory -Path $published, $game | Out-Null
$bootstrap = Join-Path $root 'bootstrap.jar'
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ReleaseZip).Path)
try {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('packwiz-installer-bootstrap.jar'), $bootstrap)
} finally { $zip.Dispose() }
function Write-Fixture([string]$Path, [string]$Content) {
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($Path)) -Force | Out-Null
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}
function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
$portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$portProbe.Start()
$port = $portProbe.LocalEndpoint.Port
$portProbe.Stop()
$url = "http://127.0.0.1:$port/"
$listener = [Net.HttpListener]::new()
$listener.Prefixes.Add($url)
$listener.Start()
try {
    Copy-Item -LiteralPath (Join-Path $repo '.packwizignore') -Destination $published
    Write-Fixture (Join-Path $published 'index.toml') "hash-format = `"sha256`"`r`n"
    Add-Content -LiteralPath (Join-Path $published '.packwizignore') -Value "`r`npayloads/"
    Write-Fixture (Join-Path $published 'pack.toml') @'
name = "Isolated automatic update test"
author = "The Gangs"
version = "1"
pack-format = "packwiz:1.1.0"
[index]
file = "index.toml"
hash-format = "sha256"
hash = ""
[versions]
minecraft = "1.20.1"
fabric = "0.19.3"
'@
    foreach ($side in @('client', 'server')) {
        $payload = Join-Path $published "payloads\$side.jar"
        Write-Fixture $payload "$side-payload"
        $hash = (Get-FileHash $payload).Hash.ToLowerInvariant()
        Write-Fixture (Join-Path $published "mods\$side.pw.toml") @"
name = "$side fixture"
filename = "$side-fixture.jar"
side = "$side"
[download]
url = "${url}payloads/$side.jar"
hash-format = "sha256"
hash = "$hash"
"@
    }
    $hashes = @{}
    foreach ($relative in @('options.txt', 'optionsof.txt', 'optionsshaders.txt', 'servers.dat', 'servers.dat_old',
            'defaultoptions.journal.json', 'config\defaultoptions\defaultoptions.journal.json', 'config\personal-mod.json',
            'mods\player-added-client-mod.jar')) {
        $path = Join-Path $game $relative
        Write-Fixture $path "personal-$relative"
        $hashes[$path] = (Get-FileHash $path).Hash
    }
    Write-Fixture (Join-Path $published 'options.txt') 'never-publish-personal-options'
    Write-Fixture (Join-Path $published 'servers.dat') 'never-publish-personal-servers'
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $pending = $listener.GetContextAsync()
    foreach ($generation in 1..2) {
        $islandMetadata = Join-Path $published 'mods\removed-island.pw.toml'
        $grimMetadata = Join-Path $published 'mods\grimac.pw.toml'
        $jeiMetadata = Join-Path $published 'mods\jei.pw.toml'
        if ($generation -eq 1) {
            $island = [IO.File]::ReadAllText((Join-Path $published 'mods\client.pw.toml'))
            Write-Fixture $islandMetadata ($island.Replace('client-fixture.jar', 'YungsBetterEndIsland-fixture.jar'))
            $grim = $island.Replace('client-fixture.jar', 'grimac-fabric-fixture.jar').Replace('side = "client"', 'side = "both"')
            Write-Fixture $grimMetadata $grim
            Write-Fixture $jeiMetadata ($island.Replace('client-fixture.jar', 'jei-fabric-fixture.jar'))
        } else {
            Remove-Item -LiteralPath $islandMetadata
            Remove-Item -LiteralPath $grimMetadata
            Remove-Item -LiteralPath $jeiMetadata
        }
        Write-Fixture (Join-Path $published 'config\defaultoptions\options.txt') "generation:$generation"
        Write-Fixture (Join-Path $published 'config\defaultoptions\keybindings.txt') "key_key.forward:generation-$generation"
        Write-Fixture (Join-Path $published 'config\defaultoptions\servers.dat') "default-server-generation-$generation"
        foreach ($relative in @('config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json',
                'config\randomcollections\config.json')) {
            if ($generation -eq 1) {
                Write-Fixture (Join-Path $published $relative) '{"old":"managed-reward-config"}'
            } else {
                Copy-Item -LiteralPath (Join-Path $repo $relative) -Destination (Join-Path $published $relative)
            }
        }
        Push-Location $published
        try {
            & packwiz refresh
            if ($LASTEXITCODE -ne 0) { throw 'Fixture Packwiz refresh failed.' }
        } finally { Pop-Location }
        $index = [IO.File]::ReadAllText((Join-Path $published 'index.toml'))
        Require ($index -notmatch '(?m)^file = "(options.txt|servers.dat)"') 'Packwiz indexed personal preferences.'
        Require ($index -match 'config/defaultoptions/options.txt' -and $index -match 'config/defaultoptions/servers.dat') 'First-run defaults must stay managed.'
        $stdout = Join-Path $root "update-$generation.log"
        $stderr = Join-Path $root "update-$generation.err"
        $process = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$bootstrap`"", '-g', '-s', 'client', "${url}pack.toml") `
            -WorkingDirectory $game -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $handle = $process.Handle
        $deadline = (Get-Date).AddMinutes(3)
        while (-not $process.HasExited) {
            if ((Get-Date) -gt $deadline) {
                Stop-Process -Id $process.Id
                throw 'Isolated automatic update timed out.'
            }
            if ($pending.Wait(100)) {
                $context = $pending.Result
                $relative = $context.Request.Url.AbsolutePath.TrimStart('/').Replace('/', '\')
                $path = [IO.Path]::GetFullPath((Join-Path $published $relative))
                if (-not $path.StartsWith($published + '\', [StringComparison]::OrdinalIgnoreCase) -or
                        -not (Test-Path -LiteralPath $path -PathType Leaf)) {
                    $context.Response.StatusCode = 404
                } else {
                    $bytes = [IO.File]::ReadAllBytes($path)
                    $context.Response.ContentLength64 = $bytes.Length
                    $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
                }
                $context.Response.Close()
                $pending = $listener.GetContextAsync()
            }
        }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            throw ("Packwiz automatic update failed:`r`n" + [IO.File]::ReadAllText($stdout) +
                [IO.File]::ReadAllText($stderr))
        }
        foreach ($path in $hashes.Keys) {
            Require ((Get-FileHash $path).Hash -ceq $hashes[$path]) "Automatic update replaced personal data: $path"
        }
        Require ([IO.File]::ReadAllText((Join-Path $game 'config\defaultoptions\options.txt')) -ceq "generation:$generation") 'Managed defaults did not update.'
        Require ([IO.File]::ReadAllText((Join-Path $game 'config\defaultoptions\keybindings.txt')) -ceq "key_key.forward:generation-$generation") 'Managed keybinding defaults did not update.'
        foreach ($relative in @('config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json',
                'config\randomcollections\config.json')) {
            Require ((Get-FileHash (Join-Path $game $relative)).Hash -eq
                (Get-FileHash (Join-Path $published $relative)).Hash) "Managed reward config did not update: $relative"
        }
        Require (Test-Path -LiteralPath (Join-Path $game 'mods\client-fixture.jar')) 'Client mod was not installed.'
        Require (-not (Test-Path -LiteralPath (Join-Path $game 'mods\server-fixture.jar'))) 'Automatic client update installed a server-only mod.'
        Require ((Test-Path -LiteralPath (Join-Path $game 'mods\YungsBetterEndIsland-fixture.jar')) -eq ($generation -eq 1)) 'Automatic update did not remove the previously managed island mod.'
        Require ((Test-Path -LiteralPath (Join-Path $game 'mods\grimac-fabric-fixture.jar')) -eq ($generation -eq 1)) 'Automatic update did not remove the previously managed GrimAC mod.'
        Require ((Test-Path -LiteralPath (Join-Path $game 'mods\jei-fabric-fixture.jar')) -eq ($generation -eq 1)) 'Automatic update did not remove previously managed JEI.'
    }
    Write-Output 'PASS: two real headless Packwiz updates; previously managed JEI removed, managed defaults updated, personal options/server lists and player-added mod jars stayed byte-identical, root preference exclusions worked, and server-only mods were skipped.'
} finally { $listener.Stop(); $listener.Close() }
Remove-Item -LiteralPath $root -Recurse
