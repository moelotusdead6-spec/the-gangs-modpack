[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string]$GameDirectory
)

$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$running = @(Get-CimInstance Win32_Process | Where-Object {
    $_.ProcessId -ne $PID -and $_.CommandLine -and
    $_.CommandLine.IndexOf($game, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
    $_.Name -in @('java.exe', 'javaw.exe', 'cmd.exe')
})
if ($running.Count) { throw 'Stop the server and close its launcher before pruning backups.' }
$properties = [IO.File]::ReadAllText((Join-Path $game 'server.properties'))
$level = [regex]::Match($properties, '(?m)^level-name=([^\r\n]+)').Groups[1].Value.Trim()
if (-not $level) { $level = 'world' }
if ([IO.Path]::IsPathRooted($level) -or $level -match '(^|[\\/])\.\.([\\/]|$)') {
    throw 'Refusing to prune backups for a world outside this server directory.'
}
$world = Join-Path $game $level
$journal = Join-Path $game 'config\goldclaim\rsw-reset.json'
if (Test-Path -LiteralPath $journal) {
    $state = [IO.File]::ReadAllText($journal) | ConvertFrom-Json
    if ($state.phase -ne 'IDLE') { throw 'RSW reset is in progress; preserve its recovery data until complete.' }
}
$targets = [Collections.Generic.List[string]]::new()
$archives = Join-Path $world 'dimensions\multiworld\rsw-reset-backups'
if (Test-Path -LiteralPath $archives) {
    foreach ($directory in Get-ChildItem -LiteralPath $archives -Directory) {
        if ($directory.Name -notmatch '^reset-\d+$') { throw "Unrecognized RSW archive: $($directory.FullName)" }
        $targets.Add($directory.FullName)
    }
}
$backupRoot = Join-Path $game 'backups'
if (Test-Path -LiteralPath $backupRoot) {
    $directories = @(Get-ChildItem -LiteralPath $backupRoot -Directory)
    foreach ($directory in $directories) {
        if ($directory.Name -match '^policy-v\d+\.\d+\.\d+-\d{8}-\d{6}-\d{4}$') {
            $end = Join-Path $directory.FullName 'rsw_the_end'
            if (Test-Path -LiteralPath $end) { $targets.Add($end) }
        }
        if ($directory.Name -match '^waystones-policy-v\d+\.\d+\.\d+$') {
            foreach ($dimension in @('rsw', 'rsw_nether', 'rsw_the_end')) {
                $rsw = Join-Path $directory.FullName ($level + '\dimensions\multiworld\' + $dimension)
                if (Test-Path -LiteralPath $rsw) { $targets.Add($rsw) }
            }
        }
    }
    $groups = $directories | Where-Object { $_.Name -match '-\d{8}(?:-\d{6}(?:-\d{4})?)?$' } |
        Group-Object { ($_.Name -replace '-\d{8}(?:-\d{6}(?:-\d{4})?)?$', '') -replace '-v\d+\.\d+\.\d+', '' }
    foreach ($group in $groups) {
        foreach ($old in @($group.Group | Sort-Object LastWriteTimeUtc -Descending | Select-Object -Skip 2)) {
            $targets.Add($old.FullName)
        }
    }
}
foreach ($directory in @($game, (Join-Path $game 'mods'), (Join-Path $game 'config'))) {
    if (-not (Test-Path -LiteralPath $directory)) { continue }
    $files = @(Get-ChildItem -LiteralPath $directory -File | Where-Object {
        $_.Name -match '\.bak(?:$|_)|\.backup(?:$|_)|\.resource-pack-backup-\d{8}-\d{6}-\d{4}$'
    })
    $groups = $files | Group-Object {
        $name = $_.Name -replace '\.resource-pack-backup-\d{8}-\d{6}-\d{4}$', ''
        $name = $name -replace '\.(?:bak|backup)(?:_.*)?$', ''
        if ($directory -eq (Join-Path $game 'mods') -and $name -match '^(.+?)-\d') {
            $Matches[1]
        } else {
            $name -replace '-\d+(?=\.toml$)', ''
        }
    }
    foreach ($group in $groups) {
        foreach ($old in @($group.Group | Sort-Object LastWriteTimeUtc -Descending | Select-Object -Skip 2)) {
            $targets.Add($old.FullName)
        }
    }
}
foreach ($target in $targets | Select-Object -Unique) {
    if (-not (Test-Path -LiteralPath $target)) { continue }
    $item = Get-Item -LiteralPath $target
    $items = @($item)
    if ($item.PSIsContainer) { $items += @(Get-ChildItem -LiteralPath $target -Recurse -Force) }
    if (@($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
        throw "Refusing to delete backup containing a link/junction: $target"
    }
    if ($PSCmdlet.ShouldProcess($target, 'Delete obsolete server backup')) {
        Remove-Item -LiteralPath $target -Recurse -Force
        Write-Output "Removed obsolete backup: $target"
    }
}
