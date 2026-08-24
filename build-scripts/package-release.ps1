<#
.SYNOPSIS
    Builds the Windows .exe installer and the Android APK, then bundles both
    with the source tree and documentation into a single release ZIP.

.DESCRIPTION
    This is the Windows half of the packaging workflow. Compose Desktop can
    only produce a Windows installer on Windows, which is why this script
    exists alongside build-scripts/package-release.sh.

.PARAMETER Version
    Version stamped into the installer and the ZIP name. Defaults to the
    mcgui.version value in gradle.properties.

.EXAMPLE
    .\build-scripts\package-release.ps1
    .\build-scripts\package-release.ps1 -Version 1.2.0
#>

[CmdletBinding()]
param(
    [string]$Version
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Version) {
    $line = Select-String -Path 'gradle.properties' -Pattern '^mcgui\.version=(.+)$'
    $Version = if ($line) { $line.Matches[0].Groups[1].Value } else { '1.0.0' }
}

$stage = Join-Path $root 'dist\stage'
$out = Join-Path $root 'dist'
$zipName = "uilabs-$Version-windows.zip"

Write-Host "==> UILabs $Version"

if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
foreach ($dir in 'desktop', 'android', 'docs', 'templates') {
    New-Item -ItemType Directory -Force -Path (Join-Path $stage $dir) | Out-Null
}

$gradle = Join-Path $root 'gradlew.bat'
$gradleArgs = @('--no-daemon', "-Pmcgui.version=$Version")

function Invoke-Gradle {
    param([string[]]$Tasks)
    & $gradle @gradleArgs @Tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed: $($Tasks -join ' ')"
    }
}

Write-Host '==> Running checks'
Invoke-Gradle @('validateProjects', 'allTests')

Write-Host '==> Building the Windows installer (.exe and .msi)'
Invoke-Gradle @(':desktopApp:packageReleaseExe', ':desktopApp:packageReleaseMsi')

Write-Host '==> Building the portable desktop jar'
Invoke-Gradle @(':desktopApp:packageUberJarForCurrentOS')

if ($env:ANDROID_HOME -or (Test-Path 'local.properties')) {
    Write-Host '==> Building the Android release APK'
    Invoke-Gradle @(':androidApp:assembleRelease')
} else {
    Write-Host '==> Skipping the APK: no Android SDK found (set ANDROID_HOME to build it)'
}

Write-Host '==> Collecting artifacts'

Get-ChildItem -Path 'desktopApp\build\compose\binaries' -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.Extension -in '.exe', '.msi' } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $stage 'desktop') }

Get-ChildItem -Path 'desktopApp\build\compose\jars' -Filter '*.jar' -ErrorAction SilentlyContinue |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $stage 'desktop') }

Get-ChildItem -Path 'androidApp\build\outputs\apk' -Recurse -Filter '*.apk' -ErrorAction SilentlyContinue |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $stage 'android') }

Copy-Item -Recurse -Force 'templates\*' (Join-Path $stage 'templates')
Copy-Item -Recurse -Force 'docs\*' (Join-Path $stage 'docs')
Copy-Item 'README.md' $stage
if (Test-Path 'LICENSE') { Copy-Item 'LICENSE' $stage }

Write-Host '==> Archiving the source tree'
& git archive --format=zip --prefix='source/' -o (Join-Path $stage 'source.zip') HEAD
if ($LASTEXITCODE -ne 0) {
    throw 'git archive failed - run this from a git checkout.'
}

$manifest = @(
    "UILabs $Version",
    "Built on $((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss')) UTC from Windows",
    '',
    'Contents:',
    '  desktop/    Windows installer (.exe, .msi) and the portable jar',
    '  android/    Android APK',
    '  templates/  Bundled .mcgui templates and one full set of sample exports',
    '  docs/       Architecture, project format and export documentation',
    '  source.zip  Complete source tree, including the Gradle build',
    '',
    'Artifacts:'
)
$manifest += (Get-ChildItem -Recurse -File $stage | ForEach-Object {
    '  ' + $_.FullName.Substring($stage.Length + 1)
} | Sort-Object)
$manifest | Set-Content (Join-Path $stage 'MANIFEST.txt')

$zipPath = Join-Path $out $zipName
Write-Host "==> Writing $zipPath"
if (Test-Path $zipPath) { Remove-Item $zipPath }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zipPath

Write-Host ''
Write-Host "Done: $zipPath"
Get-Item $zipPath | Format-List Name, Length, LastWriteTime
