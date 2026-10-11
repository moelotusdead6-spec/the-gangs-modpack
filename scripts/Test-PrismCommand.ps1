[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseZip,
    [string]$QtCore = "$env:LOCALAPPDATA\Programs\PrismLauncher\Qt6Core.dll"
)

$ErrorActionPreference = 'Stop'
if (-not [Environment]::Is64BitProcess) { throw 'Run this test in 64-bit PowerShell.' }
if (-not (Test-Path -LiteralPath $QtCore)) { throw "Prism Qt6 runtime not found: $QtCore" }
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class PrismQtSettingsTest {
    [DllImport("kernel32", CharSet=CharSet.Unicode)] static extern IntPtr LoadLibrary(string path);
    [DllImport("kernel32", CharSet=CharSet.Ansi)] static extern IntPtr GetProcAddress(IntPtr module, string name);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate IntPtr FromUtf8(IntPtr result, IntPtr data, long size);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate void SettingsConstructor(IntPtr self, IntPtr filename, int format, IntPtr parent);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate IntPtr SettingsValue(IntPtr self, IntPtr result, IntPtr key);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate IntPtr VariantString(IntPtr self, IntPtr result);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate void Destructor(IntPtr self);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] delegate IntPtr SplitCommand(IntPtr result, IntPtr view);
    static T Function<T>(IntPtr module, string name) {
        IntPtr address = GetProcAddress(module, name);
        if (address == IntPtr.Zero) throw new InvalidOperationException("Qt export not found: " + name);
        return (T)(object)Marshal.GetDelegateForFunctionPointer(address, typeof(T));
    }
    static IntPtr Storage(int size) {
        IntPtr pointer = Marshal.AllocHGlobal(size);
        Marshal.Copy(new byte[size], 0, pointer, size);
        return pointer;
    }
    static IntPtr String(FromUtf8 create, string value) {
        byte[] bytes = System.Text.Encoding.UTF8.GetBytes(value);
        IntPtr data = Storage(bytes.Length + 1), result = Storage(24);
        try { Marshal.Copy(bytes, 0, data, bytes.Length); create(result, data, bytes.Length); }
        finally { Marshal.FreeHGlobal(data); }
        return result;
    }
    static string ReadString(IntPtr value) {
        return Marshal.PtrToStringUni(Marshal.ReadIntPtr(value, 8), checked((int)Marshal.ReadInt64(value, 16)));
    }
    public static string[] ReadArguments(string library, string filename, string java, string instance) {
        IntPtr module = LoadLibrary(library);
        if (module == IntPtr.Zero) throw new InvalidOperationException("Cannot load Prism Qt6Core.");
        var create = Function<FromUtf8>(module, "?fromUtf8@QString@@SA?AV1@PEBD_J@Z");
        var construct = Function<SettingsConstructor>(module, "??0QSettings@@QEAA@AEBVQString@@W4Format@0@PEAVQObject@@@Z");
        var value = Function<SettingsValue>(module, "?value@QSettings@@QEBA?AVQVariant@@AEBVQString@@@Z");
        var text = Function<VariantString>(module, "?toString@QVariant@@QEBA?AVQString@@XZ");
        var destroySettings = Function<Destructor>(module, "??1QSettings@@UEAA@XZ");
        var destroyString = Function<Destructor>(module, "??1QString@@QEAA@XZ");
        var destroyVariant = Function<Destructor>(module, "??1QVariant@@QEAA@XZ");
        var split = Function<SplitCommand>(module, "?splitCommand@QProcess@@SA?AV?$QList@VQString@@@@VQStringView@@@Z");
        IntPtr file = String(create, filename), key = String(create, "PreLaunchCommand");
        IntPtr settings = Storage(512), variant = Storage(32), result = Storage(24);
        construct(settings, file, 1, IntPtr.Zero);
        try {
            value(settings, variant, key);
            text(variant, result);
            string command = ReadString(result).Replace("$INST_JAVA", java).Replace("$INST_DIR", instance);
            IntPtr commandString = String(create, command), view = Storage(16), list = Storage(24);
            try {
                Marshal.WriteInt64(view, Marshal.ReadInt64(commandString, 16));
                Marshal.WriteIntPtr(view, 8, Marshal.ReadIntPtr(commandString, 8));
                split(list, view);
                int count = checked((int)Marshal.ReadInt64(list, 16));
                if (count < 1 || count > 32) throw new InvalidOperationException("Unexpected Qt argument count.");
                string[] arguments = new string[count];
                IntPtr data = Marshal.ReadIntPtr(list, 8);
                for (int i = 0; i < count; i++) arguments[i] = ReadString(IntPtr.Add(data, 24 * i));
                return arguments;
            } finally {
                // QList's inline destructor is not exported; these test-only native results live until process exit.
                destroyString(commandString);
                Marshal.FreeHGlobal(commandString); Marshal.FreeHGlobal(view); Marshal.FreeHGlobal(list);
            }
        } finally {
            destroyString(result); destroyVariant(variant); destroySettings(settings);
            destroyString(file); destroyString(key);
            foreach (IntPtr pointer in new[] {file, key, settings, variant, result}) Marshal.FreeHGlobal(pointer);
        }
    }
}
'@
$repo = [IO.Directory]::GetParent($PSScriptRoot).FullName
$fixture = Join-Path $repo ('tmp\prism-command-test-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
New-Item -ItemType Directory -Path $fixture | Out-Null
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ReleaseZip).Path)
try {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('instance.cfg'), (Join-Path $fixture 'instance.cfg'))
} finally { $zip.Dispose() }
. (Join-Path $PSScriptRoot 'Get-PrismAutoUpdateConfig.ps1')
$original = "[General]`nConfigVersion=1.2`nPreLaunchCommand=`"`$INST_JAVA`" -jar `"`$INST_DIR/packwiz-installer-bootstrap.jar`" -g -s client https://moelotusdead6-spec.github.io/the-gangs-modpack/pack.toml`n"
[IO.File]::WriteAllText((Join-Path $fixture 'broken.cfg'), $original)
[IO.File]::WriteAllText((Join-Path $fixture 'repaired.cfg'), (Get-PrismAutoUpdateConfig $original))
$java = 'C:/Users/Test Player/Java Runtime/bin/javaw.exe'
$instance = 'C:/Users/Test Player/Prism Instances/The Gangs'
$expected = @($java, '-jar', "$instance/packwiz-installer-bootstrap.jar", '-g', '-s', 'client',
    'https://moelotusdead6-spec.github.io/the-gangs-modpack/pack.toml')
$broken = [PrismQtSettingsTest]::ReadArguments($QtCore, (Join-Path $fixture 'broken.cfg'), $java, $instance)
if ($broken[0] -eq $java) { throw 'Regression fixture did not reproduce the broken command.' }
foreach ($file in @('instance.cfg', 'repaired.cfg')) {
    $actual = [PrismQtSettingsTest]::ReadArguments($QtCore, (Join-Path $fixture $file), $java, $instance)
    if ($actual.Count -ne $expected.Count) { throw "Qt decoded the wrong argument count: $file" }
    for ($i = 0; $i -lt $expected.Count; $i++) {
        if ($actual[$i] -cne $expected[$i]) { throw "Qt argument $i mismatch in $file`: $($actual[$i])" }
    }
}
Write-Output 'PASS: actual Prism Qt QSettings/QProcess reproduced the old bug and correctly decoded all seven arguments for new ZIP and repaired configs, including paths with spaces.'
Remove-Item -LiteralPath $fixture -Recurse
