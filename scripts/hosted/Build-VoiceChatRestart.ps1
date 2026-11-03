[CmdletBinding()]
param(
    # Byte-exact copy of the live server.properties, used by the regression tests.
    [Parameter(Mandatory)]
    [string]$LiveServerProperties
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$LiveServerProperties = (Resolve-Path -LiteralPath $LiveServerProperties).Path
$filename = 'voicechat-fabric-1.20.1-2.6.25.jar'
$url = "https://cdn.modrinth.com/data/9eGKb6K1/versions/uctY8QSQ/$filename"
$jarHash = '314765c54a4967a971836469c8ba0a31ff0718f418d54bb770fef8a97f3f1036'

# The launcher embeds an All Rights Reserved jar, so it is built into ignored tmp/ and only uploaded
# to the private server, never committed to the public repository.
$build = Join-Path $repo 'tmp\voicechat-restart-build'
$classes = Join-Path $build 'classes'
$payload = Join-Path $classes 'payload'
$output = Join-Path $repo 'tmp\voicechat-restart\gangs-voicechat-restart-1.0.66.jar'
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite existing restart launcher: $output" }
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $payload 'mods'), (Join-Path $payload 'config\voicechat'), ([IO.Path]::GetDirectoryName($output)) | Out-Null

$jar = Join-Path $payload "mods\$filename"
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $jar
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ine $jarHash) { throw 'Official voice chat jar hash mismatch.' }
$packMetadata = Get-Content -LiteralPath (Join-Path $repo 'mods\simple-voice-chat.pw.toml') -Raw
if ($packMetadata -notmatch ('(?m)^hash = "' + (Get-FileHash -LiteralPath $jar -Algorithm SHA512).Hash.ToLowerInvariant() + '"\s*$')) {
    throw 'Client pack metadata does not pin the same voice chat jar.'
}

# port=-1 reuses the game port number over UDP; Pterodactyl forwards each allocation for TCP and UDP.
# Blank bind_address uses server-ip and blank voice_host lets clients use the address they joined with.
$config = @"
# Simple Voice Chat server settings for The Gangs. Installed by gangs-voicechat-restart-1.0.66.
port=-1
bind_address=
voice_host=
max_voice_distance=48.0
enable_groups=true
force_voice_chat=false
"@
$configPath = Join-Path $payload 'config\voicechat\voicechat-server.properties'
[IO.File]::WriteAllText($configPath, $config.Replace("`r`n", "`n") + "`n", [Text.UTF8Encoding]::new($false))

$utf8 = [Text.UTF8Encoding]::new($false)
$hashes = @(
    "mods/$filename=$jarHash",
    "config/voicechat/voicechat-server.properties=$((Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash.ToLowerInvariant())"
)
[IO.File]::WriteAllLines((Join-Path $payload 'files.properties'), $hashes, $utf8)
& javac --release 17 -d $classes (Join-Path $PSScriptRoot 'VoiceChatRestart.java') (Join-Path $PSScriptRoot 'VoiceChatRestartTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Voice chat restart launcher compilation failed.' }
& java -cp $classes VoiceChatRestartTest $LiveServerProperties
if ($LASTEXITCODE -ne 0) { throw 'Voice chat restart regression tests failed.' }
$manifest = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifest, "Manifest-Version: 1.0`r`nMain-Class: VoiceChatRestart`r`nClass-Path: fabric-server-launch.jar`r`n`r`n", [Text.Encoding]::ASCII)
& jar --create --file $output --manifest $manifest -C $classes VoiceChatRestart.class -C $classes payload
if ($LASTEXITCODE -ne 0) { throw 'Voice chat restart launcher packaging failed.' }
$result = Get-FileHash -LiteralPath $output -Algorithm SHA256
Remove-Item -LiteralPath $build -Recurse -Force
$result
