function Get-PrismAutoUpdateConfig([string]$Content) {
    $general = [regex]::Matches($Content, '(?m)^\[General\][ \t]*\r?$')
    if ($general.Count -ne 1) { throw 'Expected exactly one Prism [General] section.' }
    $content = [regex]::Replace($Content,
        '(?m)^(OverrideCommands|PreLaunchCommand|PreLaunchCommandIgnoreExitCode)=[^\r\n]*\r?\n?', '')
    $settings = '[General]' + "`r`n" + 'OverrideCommands=true' + "`r`n" +
        'PreLaunchCommand="\"$INST_JAVA\" -jar \"$INST_DIR/packwiz-installer-bootstrap.jar\" -g -s client https://moelotusdead6-spec.github.io/the-gangs-modpack/pack.toml"' + "`r`n" +
        'PreLaunchCommandIgnoreExitCode=false'
    return [regex]::Replace($content, '(?m)^\[General\][ \t]*\r?$',
        [Text.RegularExpressions.MatchEvaluator]{ param($match) $settings })
}
