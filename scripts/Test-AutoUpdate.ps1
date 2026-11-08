[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseZip
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$indexCheck = Join-Path $PSScriptRoot 'Test-PackwizIndex.ps1'
& $indexCheck -PackDirectory $repo
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
    [IO.File]::WriteAllText($Path, $Content.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))
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
    $mixinName = 'mixinextras-fabric-0.5.5.jar'
    $mixinMetadata = [IO.File]::ReadAllText((Join-Path $repo 'mods\mixinextras-fabric-direct.pw.toml'))
    Require ($mixinMetadata -match '(?m)^filename = "mixinextras-fabric-0\.5\.5\.jar"\r?$' -and
        $mixinMetadata -match '(?m)^side = "client"\r?$' -and
        $mixinMetadata -match '(?m)^hash-format = "sha512"\r?$') 'MixinExtras must remain a SHA-512 pinned client mod.'
    $mixinPayload = Join-Path $published 'payloads\mixinextras.jar'
    Write-Fixture $mixinPayload 'mixinextras-client-payload'
    $mixinHash = (Get-FileHash -LiteralPath $mixinPayload -Algorithm SHA512).Hash.ToLowerInvariant()
    $fixtureMixinMetadata = [regex]::Replace($mixinMetadata, '(?m)^url = "[^"]+"\r?$',
        "url = `"${url}payloads/mixinextras.jar`"")
    $fixtureMixinMetadata = [regex]::Replace($fixtureMixinMetadata, '(?m)^hash = "[0-9a-f]+"\r?$',
        "hash = `"$mixinHash`"")
    Require ($fixtureMixinMetadata.Contains("url = `"${url}payloads/mixinextras.jar`"") -and
        $fixtureMixinMetadata.Contains("hash = `"$mixinHash`"")) 'Could not prepare the MixinExtras update fixture.'
    $hashes = @{}
    foreach ($relative in @('options.txt', 'optionsof.txt', 'optionsshaders.txt', 'servers.dat', 'servers.dat_old',
            'defaultoptions.journal.json', 'config\defaultoptions\defaultoptions.journal.json', 'config\personal-mod.json',
            'mods\player-added-client-mod.jar')) {
        $path = Join-Path $game $relative
        Write-Fixture $path "personal-$relative"
        $hashes[$path] = (Get-FileHash $path).Hash
    }
    $instanceMetadata = Join-Path $root 'mmc-pack.json'
    $initialInstance = '{"formatVersion":1,"components":[{"uid":"net.minecraft","version":"1.20.1"},{"uid":"net.fabricmc.fabric-loader","version":"0.19.3"}]}'
    Write-Fixture $instanceMetadata $initialInstance
    function Get-FabricVersion([string]$Path) {
        $instance = [IO.File]::ReadAllText($Path) | ConvertFrom-Json
        $loaders = @($instance.components | Where-Object { $_.uid -eq 'net.fabricmc.fabric-loader' })
        Require ($loaders.Count -eq 1) "Expected exactly one Fabric Loader component in $Path."
        return $loaders[0].version
    }
    Write-Fixture (Join-Path $published 'options.txt') 'never-publish-personal-options'
    Write-Fixture (Join-Path $published 'servers.dat') 'never-publish-personal-servers'
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $pending = $listener.GetContextAsync()
    function Invoke-FixtureUpdate([string]$Name, [ref]$NextRequest, [string]$GameDirectory = $game, [string]$Side = 'client') {
        $stdout = Join-Path $root "$Name.log"
        $stderr = Join-Path $root "$Name.err"
        $process = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$bootstrap`"", '-g', '-s', $Side, "${url}pack.toml") `
            -WorkingDirectory $GameDirectory -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $handle = $process.Handle
        $deadline = (Get-Date).AddMinutes(3)
        while (-not $process.HasExited) {
            if ((Get-Date) -gt $deadline) {
                Stop-Process -Id $process.Id
                throw 'Isolated automatic update timed out.'
            }
            if ($NextRequest.Value.Wait(100)) {
                $context = $NextRequest.Value.Result
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
                $NextRequest.Value = $listener.GetContextAsync()
            }
        }
        $process.WaitForExit()
        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            Log = [IO.File]::ReadAllText($stdout) + [IO.File]::ReadAllText($stderr)
        }
    }
    function Require-IndexFailure([string]$Expected) {
        $failed = $false
        try {
            & $indexCheck -PackDirectory $published
        } catch {
            if (-not $_.Exception.Message.Contains($Expected)) { throw }
            $failed = $true
        }
        Require $failed "Expected Packwiz index validation to report: $Expected"
    }
    $regressionDoc = Join-Path $published 'ARTIFACTS.md'
    [IO.File]::WriteAllText($regressionDoc, "fixture`r`n", [Text.UTF8Encoding]::new($false))
    Push-Location $published
    try {
        & packwiz refresh
        if ($LASTEXITCODE -ne 0) { throw 'Fixture Packwiz refresh failed.' }
    } finally { Pop-Location }
    Require-IndexFailure 'Git will transform: ARTIFACTS.md'
    Write-Fixture $regressionDoc "fixture`n"
    Require-IndexFailure 'Hash mismatch: ARTIFACTS.md'
    $failedUpdate = Invoke-FixtureUpdate 'checksum-mismatch' ([ref]$pending)
    Require ($failedUpdate.ExitCode -ne 0 -and $failedUpdate.Log -match '(?i)ARTIFACTS\.md' -and
        $failedUpdate.Log -match '(?i)hash|checksum') "The updater did not reject the checksum mismatch:`r`n$($failedUpdate.Log)"

    $oldMapPayload = Join-Path $published 'payloads\goldclaimmap-old.jar'
    $newMapPayload = Join-Path $published 'payloads\goldclaimmap-new.jar'
    $retiredMapPayload = Join-Path $published 'payloads\goldclaimmap-retired.jar'
    Write-Fixture $oldMapPayload 'old-map-payload'
    Write-Fixture $newMapPayload 'new-map-payload'
    Write-Fixture $retiredMapPayload 'retired-map-placeholder'
    function Write-MapFixture([string]$Path, [string]$Name, [string]$FileName, [string]$Payload) {
        $hash = (Get-FileHash -LiteralPath $Payload -Algorithm SHA256).Hash.ToLowerInvariant()
        $payloadName = [IO.Path]::GetFileName($Payload)
        Write-Fixture $Path @"
name = "$Name"
filename = "$FileName"
side = "client"
[download]
url = "${url}payloads/$payloadName"
hash-format = "sha256"
hash = "$hash"
"@
    }

    foreach ($generation in 1..2) {
        $islandMetadata = Join-Path $published 'mods\removed-island.pw.toml'
        $grimMetadata = Join-Path $published 'mods\grimac.pw.toml'
        $jeiMetadata = Join-Path $published 'mods\jei.pw.toml'
        $mapMetadata = Join-Path $published 'mods\goldclaimmap-direct.pw.toml'
        $retiredMapMetadata = Join-Path $published 'mods\retired-goldclaimmap-1-0-2.pw.toml'
        if ($generation -eq 1) {
            $island = [IO.File]::ReadAllText((Join-Path $published 'mods\client.pw.toml'))
            Write-Fixture $islandMetadata ($island.Replace('client-fixture.jar', 'YungsBetterEndIsland-fixture.jar'))
            $grim = $island.Replace('client-fixture.jar', 'grimac-fabric-fixture.jar').Replace('side = "client"', 'side = "both"')
            Write-Fixture $grimMetadata $grim
            Write-Fixture $jeiMetadata ($island.Replace('client-fixture.jar', 'jei-fabric-fixture.jar'))
            Write-MapFixture $mapMetadata 'GoldClaim Map' 'goldclaimmap-1.0.2.jar' $oldMapPayload
        } else {
            Remove-Item -LiteralPath $islandMetadata
            Remove-Item -LiteralPath $grimMetadata
            Remove-Item -LiteralPath $jeiMetadata
            Write-MapFixture $mapMetadata 'GoldClaim Map' 'goldclaimmap-1.0.3.jar' $newMapPayload
            Write-MapFixture $retiredMapMetadata 'Retired: goldclaimmap-1.0.2.jar' 'goldclaimmap-1.0.2.jar' $retiredMapPayload
            Write-Fixture (Join-Path $published 'mods\mixinextras-fabric-direct.pw.toml') $fixtureMixinMetadata
            $fixturePack = Join-Path $published 'pack.toml'
            $packText = [IO.File]::ReadAllText($fixturePack)
            Require ($packText.Contains('fabric = "0.19.3"')) 'The first-generation Fabric Loader pin is missing.'
            Write-Fixture $fixturePack ($packText.Replace('fabric = "0.19.3"', 'fabric = "0.19.5"'))
        }
        Write-Fixture (Join-Path $published 'config\defaultoptions\options.txt') "generation:$generation"
        Write-Fixture (Join-Path $published 'config\defaultoptions\keybindings.txt') "key_key.forward:generation-$generation"
        Write-Fixture (Join-Path $published 'config\defaultoptions\servers.dat') "default-server-generation-$generation"
        foreach ($relative in @('config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json',
                'config\randomcollections\config.json',
                'config\fancymenu\options.txt',
                'config\fancymenu\customization\title_screen_layout.txt')) {
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
        & $indexCheck -PackDirectory $published
        $index = [IO.File]::ReadAllText((Join-Path $published 'index.toml'))
        Require ($index -notmatch '(?m)^file = "(options.txt|servers.dat)"') 'Packwiz indexed personal preferences.'
        Require ($index -match 'config/defaultoptions/options.txt' -and $index -match 'config/defaultoptions/servers.dat') 'First-run defaults must stay managed.'
        $update = Invoke-FixtureUpdate "update-$generation" ([ref]$pending)
        if ($update.ExitCode -ne 0) {
            throw ("Packwiz automatic update failed:`r`n" + $update.Log)
        }
        foreach ($path in $hashes.Keys) {
            Require ((Get-FileHash $path).Hash -ceq $hashes[$path]) "Automatic update replaced personal data: $path"
        }
        Require ([IO.File]::ReadAllText((Join-Path $game 'config\defaultoptions\options.txt')) -ceq "generation:$generation") 'Managed defaults did not update.'
        Require ([IO.File]::ReadAllText((Join-Path $game 'config\defaultoptions\keybindings.txt')) -ceq "key_key.forward:generation-$generation") 'Managed keybinding defaults did not update.'
        foreach ($relative in @('config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json',
                'config\randomcollections\config.json',
                'config\fancymenu\options.txt',
                'config\fancymenu\customization\title_screen_layout.txt')) {
            Require ((Get-FileHash (Join-Path $game $relative)).Hash -eq
                (Get-FileHash (Join-Path $published $relative)).Hash) "Managed config did not update: $relative"
        }
        Require (Test-Path -LiteralPath (Join-Path $game 'mods\client-fixture.jar')) 'Client mod was not installed.'
        Require (-not (Test-Path -LiteralPath (Join-Path $game 'mods\server-fixture.jar'))) 'Automatic client update installed a server-only mod.'
        $installedMixin = Join-Path $game "mods\$mixinName"
        Require ((Test-Path -LiteralPath $installedMixin) -eq ($generation -eq 2)) 'MixinExtras was not installed automatically during the update.'
        if ($generation -eq 2) {
            Require ((Get-FileHash -LiteralPath $installedMixin -Algorithm SHA512).Hash.ToLowerInvariant() -ceq $mixinHash) 'Installed MixinExtras payload did not match its checksum.'
        }
        $expectedLoader = if ($generation -eq 1) { '0.19.3' } else { '0.19.5' }
        Require ((Get-FabricVersion $instanceMetadata) -ceq $expectedLoader) 'The headless updater did not apply the expected Fabric Loader component.'
        Require ((Test-Path -LiteralPath (Join-Path $game 'mods\YungsBetterEndIsland-fixture.jar')) -eq ($generation -eq 1)) 'Automatic update did not remove the previously managed island mod.'
        Require ((Test-Path -LiteralPath (Join-Path $game 'mods\grimac-fabric-fixture.jar')) -eq ($generation -eq 1)) 'Automatic update did not remove the previously managed GrimAC mod.'
        Require ((Test-Path -LiteralPath (Join-Path $game 'mods\jei-fabric-fixture.jar')) -eq ($generation -eq 1)) 'Automatic update did not remove previously managed JEI.'
        $oldMap = Join-Path $game 'mods\goldclaimmap-1.0.2.jar'
        $newMap = Join-Path $game 'mods\goldclaimmap-1.0.3.jar'
        $expectedOldMap = if ($generation -eq 1) { $oldMapPayload } else { $retiredMapPayload }
        Require ((Get-FileHash -LiteralPath $oldMap).Hash -ceq (Get-FileHash -LiteralPath $expectedOldMap).Hash) 'The old map jar did not receive the expected retirement placeholder.'
        Require ((Test-Path -LiteralPath $newMap) -eq ($generation -eq 2)) 'The new map jar was not installed at the expected generation.'
        if ($generation -eq 2) {
            Require ((Get-FileHash -LiteralPath $newMap).Hash -ceq (Get-FileHash -LiteralPath $newMapPayload).Hash) 'The new map jar did not match its payload.'
        }
    }
    $existingInstance = Join-Path $root 'existing-instance'
    $existingGame = Join-Path $existingInstance 'minecraft'
    New-Item -ItemType Directory -Path (Join-Path $existingGame 'mods') -Force | Out-Null
    $existingMetadata = Join-Path $existingInstance 'mmc-pack.json'
    Write-Fixture $existingMetadata $initialInstance
    Copy-Item -LiteralPath $mixinPayload -Destination (Join-Path $existingGame "mods\$mixinName")
    $existing = Invoke-FixtureUpdate 'existing-mixinextras' ([ref]$pending) $existingGame
    Require ($existing.ExitCode -eq 0) "Automatic update failed with an existing MixinExtras jar:`r`n$($existing.Log)"
    Require ((Get-FileHash -LiteralPath (Join-Path $existingGame "mods\$mixinName") -Algorithm SHA512).Hash.ToLowerInvariant() -ceq $mixinHash) 'The existing MixinExtras jar was not preserved correctly.'
    Require ((Get-FabricVersion $existingMetadata) -ceq '0.19.5') 'The existing instance did not receive the Fabric Loader update.'

    $serverGame = Join-Path $root 'server'
    New-Item -ItemType Directory -Path $serverGame | Out-Null
    $server = Invoke-FixtureUpdate 'server-side' ([ref]$pending) $serverGame 'server'
    Require ($server.ExitCode -eq 0) "Isolated server-side update failed:`r`n$($server.Log)"
    Require (-not (Test-Path -LiteralPath (Join-Path $serverGame "mods\$mixinName"))) 'A server-side update installed client-only MixinExtras.'
    Require (Test-Path -LiteralPath (Join-Path $serverGame 'mods\server-fixture.jar')) 'A server-side update skipped the server fixture.'
    Write-Output 'PASS: Packwiz checksum failure and recovery, map mod retirement and upgrade, automatic Fabric Loader and client-only MixinExtras updates, an existing matching jar, server-side exclusion, managed defaults, personal settings and mod preservation.'
} finally { $listener.Stop(); $listener.Close() }
Remove-Item -LiteralPath $root -Recurse
