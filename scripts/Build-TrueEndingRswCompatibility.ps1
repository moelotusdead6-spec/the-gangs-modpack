[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TrueEndingJar
)

$ErrorActionPreference = 'Stop'
$expected = '4b4bc616a823fca5e91dd149464f2a92b18797cd4f1517bab73b45a77e2337d480c858a1297ee1bb2e44725725e817e59f904e33df62b32d0b7a2b3fffe92ebd'
if ((Get-FileHash -LiteralPath $TrueEndingJar -Algorithm SHA512).Hash.ToLowerInvariant() -ne $expected) {
    throw 'Compatibility generation requires the official True Ending 1.1.0c jar.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$pack = Join-Path $repo 'config\paxi\datapacks\gangs_fixes'
$utf8 = [Text.UTF8Encoding]::new($false)
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $TrueEndingJar).Path)
$written = 0
try {
    foreach ($entry in $zip.Entries | Where-Object {
        $_.FullName -match '^data/true_ending/(functions/.*\.mcfunction|predicates/.*\.json|advancements/.*\.json)$'
    }) {
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $original = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $updated = [regex]::Replace($original, '\bin (?:minecraft:)?the_end\b', 'in multiworld:rsw_the_end')
        $updated = $updated.Replace('"minecraft:the_end"', '"multiworld:rsw_the_end"')
        if ($entry.FullName -eq 'data/true_ending/functions/tick.mcfunction') {
            $relative = 'data\gangs\functions\true_ending\rsw_tick.mcfunction'
        } elseif ($updated -cne $original) {
            $relative = $entry.FullName.Replace('/', '\')
        } else {
            continue
        }
        $updated = [regex]::Replace($updated.Replace("`r`n", "`n"), '[ \t]+(?=\n|$)', '').TrimEnd("`n") + "`n"
        $path = Join-Path $pack $relative
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($path)) -Force | Out-Null
        [IO.File]::WriteAllText($path, $updated, $utf8)
        $written++
    }
} finally { $zip.Dispose() }
if ($written -lt 5) { throw 'Expected True Ending dimension compatibility resources were not found.' }
[IO.File]::WriteAllText((Join-Path $pack 'data\true_ending\functions\tick.mcfunction'),
    "execute in multiworld:rsw_the_end run function gangs:true_ending/rsw_tick`n", $utf8)
Write-Output "Generated $written True Ending RSW compatibility resources from the verified upstream jar."
