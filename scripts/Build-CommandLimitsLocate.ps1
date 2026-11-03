[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$original = Join-Path $repo 'artifacts\commandlimits-1.0.0.jar'
if ((Get-FileHash -LiteralPath $original).Hash -ine '0d95509d5096568ff6aca8f77e7ed9e470408a24b61846987e14bb8deb999e63') {
    throw 'Unexpected Command Limits baseline.'
}
$output = Join-Path $repo 'artifacts\commandlimits-1.0.1.jar'
Copy-Item -LiteralPath $original -Destination $output -Force
$zip = [IO.Compression.ZipFile]::Open($output, [IO.Compression.ZipArchiveMode]::Update)
$utf8 = [Text.UTF8Encoding]::new($false)
try {
    foreach ($name in @('commandlimits.mixins.json', 'fabric.mod.json')) {
        $entry = $zip.GetEntry($name)
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $json = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($name -eq 'commandlimits.mixins.json') {
            if (@($json.mixins | Where-Object { $_ -eq 'DisableLocateBiomeMixin' }).Count -ne 1) {
                throw 'Expected exactly one locate-disable mixin.'
            }
            $json.mixins = @($json.mixins | Where-Object { $_ -ne 'DisableLocateBiomeMixin' })
        } else {
            $json.version = '1.0.1'
            $json.depends | Add-Member -NotePropertyName 'goldclaim' -NotePropertyValue '>=1.0.59'
            $json.description = 'Existing claim protections and playtime command; locate restored with GoldClaim staff-only access.'
        }
        $entry.Delete()
        $entry = $zip.CreateEntry($name)
        $writer = [IO.StreamWriter]::new($entry.Open(), $utf8)
        try { $writer.Write(($json | ConvertTo-Json -Depth 20)) } finally { $writer.Dispose() }
    }
    $zip.GetEntry('com/gangs/commandlimits/mixin/DisableLocateBiomeMixin.class').Delete()
} finally { $zip.Dispose() }
Get-FileHash -LiteralPath $output
