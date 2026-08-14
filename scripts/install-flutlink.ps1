<#
.SYNOPSIS
  Installs the FlutLink desktop client from the latest GitHub release.

.DESCRIPTION
  Queries the GitHub Releases API for the FlutLink repository
  (github.com/OseMine/FlutLink), downloads the installer matching the current
  platform, verifies its SHA-256 checksum against the GitHub asset digest
  (when available) and runs it. Works with PowerShell 7+ on Windows, macOS and
  Linux.

  When piped directly into `iex`, the script runs with default parameters
  (latest stable release, standard download directory, install afterwards).
  Save it to a file first to pass parameters.

.PARAMETER Tag
  Install a specific release tag (e.g. "v0.1.0"). Defaults to the latest
  stable release.

.PARAMETER DownloadDir
  Directory used for the downloaded installer. Defaults to "$env:TEMP\FlutLink".

.PARAMETER NoRun
  Only download (and verify); do not launch the installer.

.PARAMETER NoVerify
  Skip the SHA-256 checksum verification (not recommended).

.EXAMPLE
  iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1)

.EXAMPLE
  curl.exe -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1 | iex

.EXAMPLE
  irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.ps1 -OutFile install-flutlink.ps1
  ./install-flutlink.ps1 -Tag v0.1.0 -NoRun
#>
[CmdletBinding()]
param(
    [string]$Tag,
    [string]$DownloadDir,
    [switch]$NoRun,
    [switch]$NoVerify
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Repo = 'OseMine/FlutLink'
$UserAgent = 'FlutLink-install-script (pwsh; +https://github.com/OseMine/FlutLink)'

function Get-Platform {
    if ($env:OS -eq 'Windows_NT') { return 'windows' }
    if ($IsMacOS) { return 'macos' }
    if ($IsLinux) { return 'linux' }
    throw 'Unsupported operating system (PowerShell 7+ is required on macOS and Linux).'
}

function Get-Release {
    $url = if ($Tag) {
        "https://api.github.com/repos/$Repo/releases/tags/$Tag"
    } else {
        "https://api.github.com/repos/$Repo/releases/latest"
    }
    return Invoke-RestMethod -Uri $url -Headers @{ 'User-Agent' = $UserAgent }
}

function Find-Asset($assets, [string]$platform) {
    switch ($platform) {
        'windows' {
            $a = $assets | Where-Object { $_.name -like '*_x64-setup.exe' } | Select-Object -First 1
            if (-not $a) { $a = $assets | Where-Object { $_.name -like '*.msi' } | Select-Object -First 1 }
            return $a
        }
        'macos' {
            $isArm = try { (& uname -m 2>$null) -match 'arm|aarch64' } catch { $false }
            $pattern = if ($isArm) { '*_aarch64.dmg' } else { '*_x64.dmg' }
            $a = $assets | Where-Object { $_.name -like $pattern } | Select-Object -First 1
            if (-not $a) { $a = $assets | Where-Object { $_.name -like '*.dmg' } | Select-Object -First 1 }
            return $a
        }
        'linux' {
            $a = $assets | Where-Object { $_.name -like '*.AppImage' } | Select-Object -First 1
            if (-not $a) { $a = $assets | Where-Object { $_.name -like '*.deb' } | Select-Object -First 1 }
            return $a
        }
    }
    return $null
}

function Install-Artifact([string]$path, [string]$platform) {
    switch ($platform) {
        'windows' {
            $ext = [IO.Path]::GetExtension($path).ToLowerInvariant()
            if ($ext -eq '.msi') {
                $p = Start-Process -FilePath 'msiexec' -ArgumentList @('/i', "`"$path`"", '/qb', '/norestart') -Wait -PassThru
            } else {
                $p = Start-Process -FilePath $path -ArgumentList '/S' -Wait -PassThru
            }
            if ($p.ExitCode -ne 0) { throw "Installer exited with code $($p.ExitCode)." }
        }
        'macos' {
            $mount = & hdiutil attach -nobrowse -readonly -quiet $path |
                Select-String -Pattern '/Volumes/' |
                ForEach-Object { ($_ -split '\s+')[-1] } |
                Select-Object -Last 1
            if (-not $mount) { throw 'Could not mount the DMG.' }
            try {
                $app = Get-ChildItem $mount -Filter '*.app' | Select-Object -First 1
                if (-not $app) { throw 'No .app bundle found in the DMG.' }
                $dest = Join-Path '/Applications' $app.Name
                if (-not (Test-Path $dest)) {
                    & ditto $app.FullName $dest
                    if ($LASTEXITCODE -ne 0) { throw 'Copying the app to /Applications failed.' }
                }
                Write-Host "Installed $dest"
            } finally {
                & hdiutil detach $mount -quiet
            }
        }
        'linux' {
            if ($path -like '*.AppImage') {
                $binDir = if (Test-Path (Join-Path $HOME '.local/bin')) { Join-Path $HOME '.local/bin' } else { '/usr/local/bin' }
                if (-not (Test-Path $binDir)) {
                    $binDir = Join-Path $HOME '.local/bin'
                    New-Item -ItemType Directory -Force -Path $binDir | Out-Null
                }
                $target = Join-Path $binDir 'FlutLink.AppImage'
                Copy-Item -Force $path $target
                & chmod +x $target
                if ($LASTEXITCODE -ne 0) { throw 'Making the AppImage executable failed.' }
                Write-Host "Installed $target"
            } else {
                $dpkgArgs = @('dpkg', '-i', $path)
                if (Get-Command sudo -ErrorAction SilentlyContinue) {
                    & sudo $dpkgArgs
                } elseif (Get-Command pkexec -ErrorAction SilentlyContinue) {
                    & pkexec $dpkgArgs
                } else {
                    throw 'Neither sudo nor pkexec is available to install the .deb.'
                }
                if ($LASTEXITCODE -ne 0) { throw 'dpkg install failed.' }
            }
        }
    }
}

$platform = Get-Platform
Write-Host "Platform: $platform"

$release = Get-Release
Write-Host "Release: $($release.tag_name) - $($release.name)"

$asset = Find-Asset $release.assets $platform
if (-not $asset) {
    throw "No installer asset found for $platform in $($release.tag_name)."
}

$downloadDir = if ($DownloadDir) { $DownloadDir } else { Join-Path $env:TEMP 'FlutLink' }
New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null

$installerPath = Join-Path $downloadDir $asset.name
if (-not (Test-Path $installerPath) -or (Get-Item $installerPath).Length -ne $asset.size) {
    Write-Host "Downloading $($asset.name) ($([Math]::Round($asset.size / 1MB, 1)) MiB) ..."
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $installerPath -Headers @{ 'User-Agent' = $UserAgent }
} else {
    Write-Host "Using cached $installerPath"
}

if (-not $NoVerify) {
    if ($asset.digest) {
        $expected = ($asset.digest -replace '^sha256:', '').ToLowerInvariant()
        $actual = (Get-FileHash -Algorithm SHA256 -Path $installerPath).Hash.ToLowerInvariant()
        if ($expected -ne $actual) {
            throw "SHA-256 mismatch for $($asset.name). Refusing to run the installer."
        }
        Write-Host 'SHA-256 verified.'
    } else {
        Write-Warning 'GitHub did not provide a digest for this asset; skipping checksum verification.'
    }
}

if ($NoRun) {
    Write-Host "Downloaded installer: $installerPath"
} else {
    Install-Artifact $installerPath $platform
    Write-Host "FlutLink $($release.tag_name.TrimStart('v')) installed."
}
