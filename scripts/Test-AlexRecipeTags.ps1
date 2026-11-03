[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ModsDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$tags = @{}
$recipes = @{}

function Read-Resource([string]$Name, [string]$Text) {
    if ($Name -match '^data/([^/]+)/tags/items/(.+)\.json$') {
        $id = "$($Matches[1]):$($Matches[2])"
        $json = $Text | ConvertFrom-Json
        if ($json.replace -or -not $tags.ContainsKey($id)) { $tags[$id] = @() }
        $tags[$id] += @($json.values)
    } elseif ($Name -match '^data/(alexsmobs|alexscaves)/recipes/(.+)\.json$') {
        $recipes["$($Matches[1]):$($Matches[2])"] = $Text
    }
}

$mods = @(Get-ChildItem -LiteralPath $ModsDirectory -Filter '*.jar' -File)
if (-not @($mods | Where-Object Name -Like 'alexsmobs-2.0.12-*.jar').Count) {
    throw 'The test requires the pinned Alexs Mobs Continued 2.0.12 jar.'
}
$mods = @($mods | Where-Object Name -NotLike 'alexscaves-*.jar')
$mods += Get-Item -LiteralPath (Join-Path $repo 'artifacts\alexscaves-2.1.8.jar')
foreach ($mod in $mods) {
    $zip = [IO.Compression.ZipFile]::OpenRead($mod.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notmatch '^data/[^/]+/tags/items/.+\.json$|^data/(alexsmobs|alexscaves)/recipes/.+\.json$') { continue }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { Read-Resource $entry.FullName $reader.ReadToEnd() }
            finally { $reader.Dispose() }
        }
    } finally { $zip.Dispose() }
}

foreach ($pack in Get-ChildItem -LiteralPath (Join-Path $repo 'config\paxi\datapacks') -Directory) {
    foreach ($file in Get-ChildItem -LiteralPath $pack.FullName -Recurse -File -Filter '*.json') {
        $relative = $file.FullName.Substring($pack.FullName.Length + 1).Replace('\', '/')
        Read-Resource $relative ([IO.File]::ReadAllText($file.FullName))
    }
}

function Resolve-Items([string]$Tag, [string[]]$Parents = @()) {
    if ($Parents -contains $Tag) { throw "Cyclic ingredient tag: $($Parents -join ' -> ') -> $Tag" }
    if (-not $tags.ContainsKey($Tag)) { throw "Missing ingredient tag: $Tag" }
    foreach ($entry in $tags[$Tag]) {
        $id = if ($entry -is [string]) { $entry } else { $entry.id }
        $required = $entry -is [string] -or $entry.required -ne $false
        if ($id.StartsWith('#')) {
            $nested = $id.Substring(1)
            if ($required -or $tags.ContainsKey($nested)) {
                Resolve-Items $nested (@($Parents) + $Tag)
            }
        } else {
            $id
        }
    }
}

$checked = 0
foreach ($recipe in $recipes.GetEnumerator()) {
    foreach ($match in [regex]::Matches($recipe.Value, '"tag"\s*:\s*"((?:c|forge):[^"]+)"')) {
        $tag = $match.Groups[1].Value
        $items = @(Resolve-Items $tag)
        if (-not $items.Count) { throw "Empty ingredient tag $tag in $($recipe.Key)" }
        $checked++
    }
}

$expected = @{
    'c:ingots/netherite' = 'minecraft:netherite_ingot'
    'c:nuggets/iron' = 'minecraft:iron_nugget'
    'c:rods/wooden' = 'minecraft:stick'
    'c:glass_blocks' = 'minecraft:glass'
    'forge:barrels/wooden' = 'minecraft:barrel'
    'forge:gems/diamond' = 'minecraft:diamond'
    'forge:glass/colorless' = 'minecraft:glass'
    'forge:gravel' = 'minecraft:gravel'
    'forge:nuggets/gold' = 'minecraft:gold_nugget'
    'forge:rods/wooden' = 'minecraft:stick'
    'forge:storage_blocks/gold' = 'minecraft:gold_block'
    'forge:storage_blocks/redstone' = 'minecraft:redstone_block'
}
$colors = @('white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
    'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black')
foreach ($color in $colors) { $expected["forge:dyes/$color"] = "minecraft:$($color)_dye" }
foreach ($tag in $expected.Keys) {
    if (@(Resolve-Items $tag) -notcontains $expected[$tag]) {
        throw "Expected $($expected[$tag]) in $tag"
    }
}
$concrete = @(Resolve-Items 'forge:concrete')
foreach ($color in $colors) {
    if ($concrete -notcontains "minecraft:$($color)_concrete") { throw "Missing $color concrete" }
}
$compat = Join-Path $repo 'config\paxi\datapacks\gangs_fixes\data'
foreach ($namespace in @('c', 'forge')) {
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $compat "$namespace\tags\items") -Recurse -File) {
        $json = [IO.File]::ReadAllText($file.FullName) | ConvertFrom-Json
        if ($json.replace -ne $false) { throw "Compatibility tag must merge, not replace: $($file.FullName)" }
    }
}
$board = $recipes['alexsmobs:straddleboard'] | ConvertFrom-Json
if (($board.pattern -join '|') -cne ' SN|SNS|NS ' -or
    $board.key.N.tag -ne 'c:ingots/netherite' -or
    $board.key.S.item -ne 'alexsmobs:straddlite' -or
    $board.result.item -ne 'alexsmobs:straddleboard' -or $board.result.count -ne 1) {
    throw 'Straddleboard recipe layout, ingredients or output changed.'
}
Write-Host "PASS: $($recipes.Count) Alex recipes scanned; $checked common-tag ingredients resolve; all 29 repaired tags contain expected ingredients; Straddleboard layout and output preserved."
