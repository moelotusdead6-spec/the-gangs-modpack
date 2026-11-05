[CmdletBinding()]
param(
    [string]$PackDirectory
)

$ErrorActionPreference = 'Stop'
if (-not $PackDirectory) { $PackDirectory = Split-Path -Parent $PSScriptRoot }
$root = (Resolve-Path -LiteralPath $PackDirectory).Path
$errors = New-Object 'System.Collections.Generic.List[string]'
$paths = New-Object 'System.Collections.Generic.List[string]'

function Get-Property([string]$Text, [string]$Name, [string]$Context) {
    $pattern = '(?m)^' + [regex]::Escape($Name) + '[ \t]*=[ \t]*"([^"]+)"[ \t]*\r?$'
    $matches = [regex]::Matches($Text, $pattern)
    if ($matches.Count -ne 1) { throw "Expected one $Name in $Context." }
    return $matches[0].Groups[1].Value
}

function Get-PackFile([string]$Relative) {
    if ($Relative.Contains('\') -or [IO.Path]::IsPathRooted($Relative) -or
            (($Relative -split '/') -contains '..')) {
        throw "Invalid Packwiz file path: $Relative"
    }
    $path = [IO.Path]::GetFullPath((Join-Path $root $Relative.Replace('/', '\')))
    $prefix = $root.TrimEnd('\') + '\'
    if (-not $path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Packwiz file is missing or outside the pack: $Relative"
    }
    return $path
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

$pack = [IO.File]::ReadAllText((Get-PackFile 'pack.toml'))
$section = [regex]::Match($pack, '(?ms)^\[index\]\r?\n(?<body>.*?)(?=^\[|\z)')
if (-not $section.Success) { throw 'pack.toml has no index section.' }
$indexSection = $section.Groups['body'].Value
$indexName = Get-Property $indexSection 'file' 'pack.toml index section'
$indexHash = Get-Property $indexSection 'hash' 'pack.toml index section'
if ((Get-Property $indexSection 'hash-format' 'pack.toml index section') -cne 'sha256' -or
        $indexHash -notmatch '^[a-f0-9]{64}$') {
    throw 'The pack index must use a SHA-256 hash.'
}
$indexPath = Get-PackFile $indexName
$actualIndexHash = Get-Sha256 $indexPath
if ($actualIndexHash -cne $indexHash) {
    $errors.Add("Hash mismatch: $indexName (expected $indexHash, got $actualIndexHash)")
}

$index = [IO.File]::ReadAllText($indexPath)
$parts = [regex]::Split($index, '(?m)^\[\[files\]\]\r?$')
if ($parts.Count -lt 2 -or (Get-Property $parts[0] 'hash-format' $indexName) -cne 'sha256') {
    throw 'The Packwiz index must contain files with SHA-256 hashes.'
}
$seen = @{}
for ($i = 1; $i -lt $parts.Count; $i++) {
    $relative = Get-Property $parts[$i] 'file' "$indexName entry $i"
    $expected = Get-Property $parts[$i] 'hash' "$indexName entry $i"
    if ($expected -notmatch '^[a-f0-9]{64}$') { throw "Invalid SHA-256 hash for $relative." }
    if ($seen.ContainsKey($relative)) { throw "Duplicate Packwiz file: $relative" }
    $seen[$relative] = $true
    $actual = Get-Sha256 (Get-PackFile $relative)
    if ($actual -cne $expected) {
        $errors.Add("Hash mismatch: $relative (expected $expected, got $actual)")
    }
    $paths.Add($relative)
}

$paths.Add($indexName)
$paths.Add('pack.toml')
$prefix = & git -C $root rev-parse --show-prefix
if ($LASTEXITCODE -ne 0) { throw 'Could not locate the pack within its Git repository.' }
$gitPaths = @($paths | ForEach-Object { ($prefix + $_).Replace('/', '\') })
$cleanHashes = @($gitPaths | & git -C $root hash-object --stdin-paths)
if ($LASTEXITCODE -ne 0) { throw 'Could not hash Packwiz files with Git clean filters.' }
$rawHashes = @($gitPaths | & git -C $root hash-object --no-filters --stdin-paths)
if ($LASTEXITCODE -ne 0) { throw 'Could not hash the unfiltered Packwiz files with Git.' }
if ($cleanHashes.Count -ne $paths.Count -or $rawHashes.Count -ne $paths.Count) {
    throw 'Git returned an unexpected number of file hashes.'
}
for ($i = 0; $i -lt $paths.Count; $i++) {
    if ($cleanHashes[$i] -cne $rawHashes[$i]) {
        $errors.Add("Git will transform: $($paths[$i]) (check line endings and clean filters)")
    }
}

if ($errors.Count -gt 0) {
    throw ("Packwiz manifest validation failed:`n- " + ($errors -join "`n- "))
}
Write-Output "PASS: Packwiz index and Git-clean content agree for $($parts.Count - 1) indexed files."
