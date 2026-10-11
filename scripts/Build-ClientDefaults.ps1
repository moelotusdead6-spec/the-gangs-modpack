[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaselineZip
)

$ErrorActionPreference = 'Stop'
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$folder = Join-Path $repo 'config\defaultoptions'
$utf8 = [Text.UTF8Encoding]::new($false)
$options = [IO.File]::ReadAllLines((Join-Path $repo 'config\options.txt'))
$selection = @($options | Where-Object { $_ -match '^resourcePacks:' })
if ($selection.Count -ne 1) { throw 'Expected one resourcePacks default.' }
$parsed = ConvertFrom-Json -InputObject $selection[0].Substring('resourcePacks:'.Length)
$selected = @($parsed)
foreach ($id in $selected) {
    if ($id -isnot [string]) { throw 'Resource-pack defaults must be strings.' }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $BaselineZip).Path)
try {
    foreach ($entry in $zip.Entries | Where-Object {
        $_.FullName -match '^minecraft/resourcepacks/[^/]+\.zip$'
    } | Sort-Object FullName) {
        $id = 'file/' + [IO.Path]::GetFileName($entry.FullName)
        if ($id -notin $selected) { $selected += $id }
    }
} finally { $zip.Dispose() }
$resourceLine = 'resourcePacks:' + (ConvertTo-Json -InputObject @($selected) -Compress)
$options = @($options | ForEach-Object {
    if ($_ -match '^resourcePacks:') { $resourceLine } else { $_ }
})
$defaultLines = @($options | Where-Object {
    $_ -notmatch '^key_' -and $_ -notmatch '^lastServer:'
})
[IO.File]::WriteAllText((Join-Path $folder 'options.txt'), ($defaultLines -join "`n") + "`n", $utf8)
$keyLines = @($options | Where-Object {
    $_ -match '^key_'
})
[IO.File]::WriteAllText((Join-Path $folder 'keybindings.txt'), ($keyLines -join "`n") + "`n", $utf8)
$server = Get-Content -LiteralPath (Join-Path $folder 'servers.json') -Raw | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($server.name) -or
        $server.ip -notmatch '^[a-zA-Z0-9.-]+(?::[0-9]+)?$') {
    throw 'Invalid public server defaults.'
}
$stream = [IO.MemoryStream]::new()
function Write-NbtString([string]$Value) {
    $bytes = $utf8.GetBytes($Value)
    if ($bytes.Length -gt 65535) { throw 'NBT string is too long.' }
    $stream.WriteByte([byte]($bytes.Length -shr 8))
    $stream.WriteByte([byte]($bytes.Length -band 255))
    $stream.Write($bytes, 0, $bytes.Length)
}
try {
    $stream.WriteByte(10)
    Write-NbtString ''
    $stream.WriteByte(9)
    Write-NbtString 'servers'
    $stream.WriteByte(10)
    $stream.Write([byte[]]@(0, 0, 0, 1), 0, 4)
    foreach ($key in @('name', 'ip')) {
        $stream.WriteByte(8)
        Write-NbtString $key
        Write-NbtString $server.$key
    }
    $stream.WriteByte(0)
    $stream.WriteByte(0)
    [IO.File]::WriteAllBytes((Join-Path $folder 'servers.dat'), $stream.ToArray())
} finally { $stream.Dispose() }
Write-Output 'Generated first-run options, keybindings and public server defaults.'
