[CmdletBinding()]
param([string]$OutputName = 'gangs-alex-weapon-restart-1.0.61.jar')
$ErrorActionPreference = 'Stop'
if ([IO.Path]::GetFileName($OutputName) -ne $OutputName -or $OutputName -notmatch '\.jar$') {
    throw 'OutputName must be a jar filename without a directory.'
}
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$build = Join-Path $repo 'tmp\alex-weapon-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'payload'
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Path $payload -Force | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
$files = @(
    @{ Source = 'artifacts\goldclaim-1.0.61.jar'; Target = 'mods/goldclaim-1.0.61.jar'; Previous = '-' },
    @{ Source = 'artifacts\commandlimits-1.0.1.jar'; Target = 'mods/commandlimits-1.0.1.jar'; Previous = '-' },
    @{ Source = 'config\gangshop\overrides.json'; Target = 'config/gangshop/overrides.json'; Previous = '96844b2537dc763d4e190d651b160e1b88647e206b65d59cedea57dd30b7ef89' },
    @{ Source = 'config\paxi\datapacks\gangs_kits\data\gangs\loot_tables\events\reward.json'; Target = 'config/paxi/datapacks/gangs_kits/data/gangs/loot_tables/events/reward.json'; Previous = '-' },
    @{ Source = 'config\randomcollections\config.json'; Target = 'config/randomcollections/config.json'; Previous = '5d243ca31e84cc7bf36abefd5773f639f596575243c0cfebc7a220d43002cc12' }
)
$collectionsOriginal = Join-Path $payload 'original\config\randomcollections\config.json'
New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($collectionsOriginal)) | Out-Null
& $env:ComSpec /c "git -C `"$repo`" show HEAD:config/randomcollections/config.json > `"$collectionsOriginal`""
if ($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath $collectionsOriginal).Hash -ine $files[-1].Previous) {
    throw 'Original random collections fixture does not match the host.'
}
foreach ($namespace in @('c', 'forge')) {
    $folder = Join-Path $repo "config\paxi\datapacks\gangs_fixes\data\$namespace\tags\items"
    foreach ($file in Get-ChildItem -LiteralPath $folder -Recurse -File | Sort-Object FullName) {
        $relative = $file.FullName.Substring($repo.Length + 1)
        $files += @{ Source = $relative; Target = $relative.Replace('\', '/'); Previous = '-' }
    }
}
$biomes = @{
    'magnetic_caves' = 'cf811ff0e46881d7993eb4910d9891ff6472554b613d3b3940deb0c6f1e0a779'
    'primordial_caves' = 'f0e2da7cf5d77abef1bd270cb7c288e3f57c001005fe41e11d545d948b06474d'
    'toxic_caves' = 'd6d7d5becfd20cca48dabcb838021694740bd874fba80a259b05d37bcc720a43'
    'abyssal_chasm' = 'd286a521c5fd852ef189e15f02952d3e6f529efbb67fe769d88d45183279c3b9'
    'forlorn_hollows' = '5eea375f709ff5d7096b51fdc32b671952e1d2aed9f48c9bd9bc8e50ec2cb7f7'
    'candy_cavity' = '02120d68d535ce346b1d66d42a039a0683ee4432b8537d70fbb2f1bf59c4d364'
}
foreach ($biome in $biomes.Keys | Sort-Object) {
    $relative = "config\alexscaves_biome_generation\$biome.json"
    $text = [IO.File]::ReadAllText((Join-Path $repo $relative)).Replace("`r`n", "`n").TrimEnd()
    $old = $text.Replace(', "multiworld:rsw"', '')
    $old = [regex]::Replace($old, '\[([^\[\]]+)\]', {
        param($match)
        $values = @($match.Groups[1].Value.Split(',') | ForEach-Object { '    ' + $_.Trim() })
        return "[`n" + ($values -join ",`n") + "`n  ]"
    })
    $original = Join-Path $payload "original\$relative"
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($original)) | Out-Null
    [IO.File]::WriteAllText($original, $old, $utf8)
    if ((Get-FileHash -LiteralPath $original).Hash -ine $biomes[$biome]) {
        throw "Original biome fixture does not match the host: $biome"
    }
    $files += @{ Source = $relative; Target = $relative.Replace('\', '/'); Previous = $biomes[$biome] }
}
if ($files.Count -ne 40) { throw 'Expected 29 recipe tags, six cave configs, shop configuration, two event reward files and two mods.' }
$hashes = @()
$previous = @()
foreach ($file in $files) {
    $source = Join-Path $repo $file.Source
    $target = Join-Path $payload $file.Target.Replace('/', '\')
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($target)) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
    $hashes += "$($file.Target)=$((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant())"
    $previous += "$($file.Target)=$($file.Previous)"
}
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $hashes, $utf8)
[IO.File]::WriteAllLines((Join-Path $payload 'previous.properties'), $previous, $utf8)
$required = @(
    'fabric-server-launch.jar=6f921315fe2f570c331ca877d1e815c29b93fdc4ad04ce0f0f5cdf4300710028',
    "config/paxi/datapacks/gangs_fixes/pack.mcmeta=$((Get-FileHash -LiteralPath (Join-Path $repo 'config\paxi\datapacks\gangs_fixes\pack.mcmeta')).Hash.ToLowerInvariant())"
)
[IO.File]::WriteAllLines((Join-Path $payload 'required.properties'), $required, $utf8)
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'AlexWeaponRestart.java') (Join-Path $PSScriptRoot 'AlexWeaponRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Restart installer compilation failed.' }
& java -cp $classes AlexWeaponRestartTest $repo
if ($LASTEXITCODE -ne 0) { throw 'Restart installer regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: AlexWeaponRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
$output = Join-Path (Join-Path $repo 'artifacts') $OutputName
& jar --create --file $output --manifest $manifest -C $classes AlexWeaponRestart.class -C $classes payload
if ($LASTEXITCODE -ne 0) { throw 'Restart installer packaging failed.' }
Get-FileHash -LiteralPath $output -Algorithm SHA256
