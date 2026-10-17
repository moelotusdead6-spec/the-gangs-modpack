$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$root = Join-Path $repo ("tmp\gameplay-hotfix-tests-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Fixture([string]$Name, [string]$PreviousVersion) {
    $game = Join-Path $root $Name
    foreach ($folder in @('mods', 'config\randomcollections', 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits')) {
        New-Item -ItemType Directory -Path (Join-Path $game $folder) -Force | Out-Null
    }
    $jar = Join-Path $game "mods\goldclaim-$PreviousVersion.jar"
    [IO.File]::WriteAllText($jar, 'old jar')
    [IO.File]::WriteAllText((Join-Path $game 'config\randomcollections\config.json'), 'old collections')
    [IO.File]::WriteAllText((Join-Path $game 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'), 'old table')
    $existingHook = if ($PreviousVersion -eq '1.0.39') {
        "powershell -NoProfile -ExecutionPolicy Bypass -File `"%~dp0Install-PendingGameplayHotfix.ps1`" -GameDirectory `"%~dp0.`"`r`nif errorlevel 1 exit /b 1`r`n"
    } else { '' }
    $hash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    $launcher = "@echo off`r`ngoto :gangs_launcher`r`nrem " + ('-' * 4000) + "`r`n:gangs_launcher`r`n" +
        $existingHook + "rem goldclaim-$PreviousVersion.jar GoldClaim $PreviousVersion $hash`r`n" +
        '"%JAVA_EXE%" @%JVM_ARGS_FILE% -jar fabric-server-launch.jar nogui' + "`r`npause`r`npopd`r`n"
    [IO.File]::WriteAllText((Join-Path $game 'start.bat'), $launcher, [Text.Encoding]::ASCII)
    return $game
}

function Expect-Rejection([string]$Game, [string]$Version, [string]$PreviousVersion) {
    $rejected = $false
    try { & (Join-Path $PSScriptRoot 'Install-PendingGameplayHotfix.ps1') -GameDirectory $Game -Version $Version }
    catch { $rejected = $true }
    Require $rejected 'Unsafe installation was not rejected.'
    Require (([IO.File]::ReadAllText((Join-Path $Game "mods\goldclaim-$PreviousVersion.jar"))) -ceq 'old jar') 'Old jar changed on preflight failure.'
    Require (-not (Test-Path (Join-Path $Game "mods\goldclaim-$Version.jar"))) 'New jar appeared after failed preflight.'
}

try {
    foreach ($version in @('1.0.39', '1.0.40')) {
        $previousVersion = if ($version -eq '1.0.40') { '1.0.39' } else { '1.0.38' }
        $game = Fixture "success-$version" $previousVersion
        $original = [IO.File]::ReadAllText((Join-Path $game 'start.bat'))
        & (Join-Path $PSScriptRoot 'Stage-GameplayHotfix.ps1') -GameDirectory $game -Version $version
        Require (([IO.File]::ReadAllText((Join-Path $game "mods\goldclaim-$previousVersion.jar"))) -ceq 'old jar') 'Staging modified the active jar.'
        Require (([IO.File]::ReadAllText((Join-Path $game 'config\randomcollections\config.json'))) -ceq 'old collections') 'Staging modified active collections.'
        Require (([IO.File]::ReadAllText((Join-Path $game 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\kits\monthly.json'))) -ceq 'old table') 'Staging modified the active table.'
        $updated = [IO.File]::ReadAllText((Join-Path $game 'start.bat'))
        $offset = $original.IndexOf('"%JAVA_EXE%"')
        Require ($original.Length -eq $updated.Length -and $updated.Substring($offset) -ceq $original.Substring($offset)) 'Launcher continuation byte offsets changed.'
        Require ($updated.Contains("-Version $version") -and $updated.Contains("goldclaim-$version.jar")) 'Launcher targets the wrong update.'
        & (Join-Path $PSScriptRoot 'Install-PendingGameplayHotfix.ps1') -GameDirectory $game -Version $version
        Require (-not (Test-Path (Join-Path $game "mods\goldclaim-$previousVersion.jar"))) 'Old jar still active.'
        Require (Test-Path (Join-Path $game "mods\goldclaim-$version.jar")) 'New jar missing.'
        Require (Test-Path (Join-Path $game "mod-backups\gameplay-$version\goldclaim-$previousVersion.jar")) 'Jar rollback backup missing.'
        Require (Test-Path (Join-Path $game "mod-backups\gameplay-$version\monthly.json")) 'Table rollback backup missing.'
        Require (Test-Path (Join-Path $game "mod-backups\gameplay-$version\collections.json")) 'Collection rollback backup missing.'
        & (Join-Path $PSScriptRoot 'Install-PendingGameplayHotfix.ps1') -GameDirectory $game -Version $version
        $game = Fixture "corrupt-$version" $previousVersion
        & (Join-Path $PSScriptRoot 'Stage-GameplayHotfix.ps1') -GameDirectory $game -Version $version
        [IO.File]::WriteAllText((Join-Path $game "pending-gameplay-$version\goldclaim-$version.jar.pending"), 'corrupted')
        Expect-Rejection $game $version $previousVersion
        $game = Fixture "changed-$version" $previousVersion
        & (Join-Path $PSScriptRoot 'Stage-GameplayHotfix.ps1') -GameDirectory $game -Version $version
        [IO.File]::WriteAllText((Join-Path $game 'config\randomcollections\config.json'), 'admin edit after staging')
        Expect-Rejection $game $version $previousVersion
    }
    Write-Host 'PASS: staging leaves active files unchanged; launcher offsets preserved; verified install, backups and repeat start pass; corruption and post-stage config edits abort before replacing active files.'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force
}
