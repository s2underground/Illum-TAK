[CmdletBinding()]
param(
    [string]$WinTakDir = "$env:ProgramW6432\WinTAK",
    [ValidateSet('Debug','Release')][string]$Configuration = 'Release',
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$repoRoot = Split-Path $projectRoot -Parent
$sdkVersion = '5.8.0.149'
if (!(Test-Path -LiteralPath "$WinTakDir\WinTak.Framework.dll")) { throw 'WinTAK SDK not found. Pass -WinTakDir with its installation directory.' }
$actualVersion = (Get-Item -LiteralPath "$WinTakDir\WinTAK.exe").VersionInfo.FileVersion
if ($actualVersion -ne $sdkVersion) { throw "This port targets WinTAK $sdkVersion; found $actualVersion. Revalidate SDK APIs before changing the target." }

& dotnet build "$projectRoot\Illum.Tests\Illum.Tests.csproj" -c $Configuration --nologo "-p:WinTakDir=$WinTakDir"
if ($LASTEXITCODE -ne 0) { throw 'Core/test build failed.' }
& "$projectRoot\Illum.Tests\bin\$Configuration\net48\Illum.Tests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Reference validation failed.' }
& dotnet build "$projectRoot\Illum.WinTAK\Illum.WinTAK.csproj" -c $Configuration --nologo "-p:WinTakDir=$WinTakDir"
if ($LASTEXITCODE -ne 0) { throw 'WinTAK plugin build failed.' }

& dotnet build "$projectRoot\Illum.SdkCheck\Illum.SdkCheck.csproj" -c $Configuration --nologo "-p:WinTakDir=$WinTakDir"
if ($LASTEXITCODE -ne 0) { throw 'SDK validation build failed.' }
& "$projectRoot\Illum.SdkCheck\bin\$Configuration\net48\Illum.SdkCheck.exe" $WinTakDir "$projectRoot\artifacts"
if ($LASTEXITCODE -ne 0) { throw 'SDK pane/rendering validation failed.' }

$output = "$projectRoot\Illum.WinTAK\bin\$Configuration\net48"
$artifacts = "$projectRoot\artifacts"
$pluginVersion = [Reflection.AssemblyName]::GetAssemblyName((Join-Path $output 'S2U.Illum.WinTAK.dll')).Version.ToString()
$wpkPath = Join-Path $artifacts "Illum-$pluginVersion-$sdkVersion.wpk"
New-Item -ItemType Directory -Path $artifacts -Force | Out-Null
Push-Location $artifacts
try {
    # WpkBuilder matches --id to PluginNameAttribute before reading variant/dependencies.
    & "$WinTakDir\WpkBuilder.exe" -i 'Illum' -d 'Illum offline night light planning for WinTAK' --plugins64 $output --icon "$repoRoot\app\src\main\res\drawable\ic_launcher.png"
    if ($LASTEXITCODE -ne 0) { throw 'WPK packaging failed.' }
} finally { Pop-Location }
$wpk = Get-Item -LiteralPath $wpkPath -ErrorAction Stop
# WpkBuilder collects assemblies only. Include notices in the plugin's own folder.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open($wpk.FullName, [IO.Compression.ZipArchiveMode]::Update)
try {
    $manifestReader = [IO.StreamReader]::new($zip.GetEntry('manifest.xml').Open())
    try { [xml]$manifest = $manifestReader.ReadToEnd() } finally { $manifestReader.Dispose() }
    $metadata = $manifest.wintakPluginPackage.metadata
    if ($metadata.id -cne 'Illum' -or $metadata.version -cne $pluginVersion -or
        $metadata.sdkVersion -cne $sdkVersion -or [string]$metadata.takVariants -cne 'Civilian') {
        throw 'WPK identity/version/SDK/variant metadata is invalid.'
    }
    $licenseFiles = @(Get-Item -LiteralPath "$projectRoot\THIRD-PARTY-NOTICES.txt") + @(Get-ChildItem -LiteralPath "$projectRoot\licenses" -File)
    foreach ($file in $licenseFiles) {
        $entryName = 'plugin/x64/Illum/' + $file.Name
        $existing = $zip.GetEntry($entryName)
        if ($existing) { $existing.Delete() }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $entryName) | Out-Null
    }
    $packagePrefix = 'plugin/x64/Illum/'
    $assemblyFiles = @('S2U.Illum.WinTAK.dll','S2U.Illum.Core.dll','NodaTime.dll')
    $expectedEntries = @('icon.png','manifest.xml') + @($assemblyFiles | ForEach-Object { $packagePrefix + $_ }) + @($licenseFiles | ForEach-Object { $packagePrefix + $_.Name })
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -cnotin $expectedEntries) { throw "Unexpected WPK entry: $($entry.FullName)" }
    }
    foreach ($entryName in $expectedEntries) {
        if (!$zip.GetEntry($entryName)) { throw "Missing WPK entry: $entryName" }
    }
    foreach ($name in $assemblyFiles) {
        $entryStream = $zip.GetEntry($packagePrefix + $name).Open()
        try { $packedHash = (Get-FileHash -InputStream $entryStream -Algorithm SHA256).Hash } finally { $entryStream.Dispose() }
        $builtHash = (Get-FileHash -LiteralPath (Join-Path $output $name) -Algorithm SHA256).Hash
        if ($packedHash -cne $builtHash) { throw "WPK assembly does not match build output: $name" }
    }
} finally { $zip.Dispose() }
$packageHash = Get-FileHash -LiteralPath $wpk.FullName -Algorithm SHA256
[IO.File]::WriteAllText($wpk.FullName + '.sha256', $packageHash.Hash + '  ' + $wpk.Name + [Environment]::NewLine, [Text.Encoding]::ASCII)
$packageHash | Format-List Path,Hash
Write-Output "Package metadata, contents and assembly hashes verified. SHA-256 sidecar: $($wpk.FullName).sha256"

if ($Install) {
    if (Get-Process WinTAK -ErrorAction SilentlyContinue) { throw 'Close WinTAK before deploying this development build.' }
    $target = Join-Path ([Environment]::GetFolderPath('ApplicationData')) 'WinTAK\Plugins\Illum'
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    # The host supplies its own Unsafe assembly; do not shadow SDK/native dependencies.
    foreach ($name in @('S2U.Illum.WinTAK.dll','S2U.Illum.Core.dll','NodaTime.dll','S2U.Illum.WinTAK.pdb','S2U.Illum.Core.pdb')) {
        Copy-Item -LiteralPath (Join-Path $output $name) -Destination $target -Force
    }
    foreach ($file in $licenseFiles) { Copy-Item -LiteralPath $file.FullName -Destination $target -Force }
    Write-Output "Development plugin deployed to $target. Open WinTAK and enable Illum if prompted."
}
