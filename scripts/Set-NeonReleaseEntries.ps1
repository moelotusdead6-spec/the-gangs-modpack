function Set-NeonReleaseEntries(
    [IO.Compression.ZipArchive]$Archive,
    [string]$ArtifactPath
) {
    $bytes = [IO.File]::ReadAllBytes($ArtifactPath)
    foreach ($entry in @($Archive.Entries | Where-Object {
        $_.FullName -match '(?i)^(minecraft|client-update|server-update)/mods/neon-wall-wonders-gwbzt3mk(?: \(\d+\))?\.jar$'
    })) {
        $entry.Delete()
    }

    foreach ($prefix in @('minecraft/', 'client-update/', 'server-update/')) {
        $entry = $Archive.CreateEntry(
            ($prefix + 'mods/neon-wall-wonders-gwbzt3mk.jar'),
            [IO.Compression.CompressionLevel]::Optimal
        )
        $stream = $entry.Open()
        try { $stream.Write($bytes, 0, $bytes.Length) } finally { $stream.Dispose() }
    }
}
