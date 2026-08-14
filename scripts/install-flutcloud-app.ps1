<#
.SYNOPSIS
  Installs the FlutCloud Nextcloud app on a Nextcloud server.

.DESCRIPTION
  Downloads the flutcloud-app sources from the FlutLink repository
  (github.com/OseMine/FlutLink) and installs them into
  <nextcloud-root>/apps/flutcloud, then enables the app with occ and verifies
  it. Run this on the machine that hosts the Nextcloud server (Linux or
  Windows host, VM) or on the Docker host using -DockerContainer.

  When piped directly into `iex`, the script runs with default parameters
  (auto-detected Nextcloud root, latest release). Save it to a file first to
  pass parameters.

.PARAMETER NextcloudRoot
  Path to the Nextcloud installation (the folder containing occ). Auto-detected
  otherwise: $env:NEXTCLOUD_ROOT, the current directory, then common locations.

.PARAMETER Ref
  Git ref to fetch the app from (release tag such as "v0.1.0" or a branch
  name). Defaults to the latest release tag, falling back to "main".

.PARAMETER WebUser
  Web-server user that owns the app files and runs occ. Default "www-data".

.PARAMETER NoSudo
  Run occ and chown directly instead of via "sudo -u". Use when already
  running as the web-server user or as root.

.PARAMETER DockerContainer
  Name or ID of a Nextcloud Docker container; occ runs via
  "docker exec -u <WebUser>".

.PARAMETER Composer
  Run "composer install --no-dev" in the app folder after copying (optional;
  the app also works without the generated autoloader).

.PARAMETER SkipVerify
  Do not verify that the app is enabled after installation.

.EXAMPLE
  iex (irm https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.ps1)

.EXAMPLE
  ./install-flutcloud-app.ps1 -NextcloudRoot /var/www/nextcloud -WebUser www-data

.EXAMPLE
  ./install-flutcloud-app.ps1 -DockerContainer nextcloud -Composer
#>
[CmdletBinding()]
param(
    [string]$NextcloudRoot,
    [string]$Ref,
    [string]$WebUser = 'www-data',
    [switch]$NoSudo,
    [string]$DockerContainer,
    [switch]$Composer,
    [switch]$SkipVerify
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Repo = 'OseMine/FlutLink'
$UserAgent = 'FlutCloud-install-script (pwsh; +https://github.com/OseMine/FlutLink)'
$IsWin = if ($null -eq $IsWindows) { $env:OS -eq 'Windows_NT' } else { $IsWindows }
$IsLinuxHost = if ($null -eq $IsLinux) { $false } else { $IsLinux }

function Resolve-NextcloudRoot {
    if ($NextcloudRoot) {
        if (-not (Test-Path (Join-Path $NextcloudRoot 'occ'))) {
            throw "'$NextcloudRoot' does not contain the Nextcloud occ script."
        }
        return (Get-Item $NextcloudRoot).FullName
    }
    if ($env:NEXTCLOUD_ROOT) {
        if (Test-Path (Join-Path $env:NEXTCLOUD_ROOT 'occ')) { return (Get-Item $env:NEXTCLOUD_ROOT).FullName }
        Write-Warning "NEXTCLOUD_ROOT is set but has no occ; ignoring it."
    }
    $cwd = (Get-Location).Path
    if (Test-Path (Join-Path $cwd 'occ')) { return $cwd }
    $candidates = @(
        '/var/www/nextcloud', '/var/www/html', '/srv/nextcloud',
        '/usr/share/webapps/nextcloud', 'C:\nextcloud', 'C:\htdocs\nextcloud'
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c 'occ')) { return $c }
    }
    throw 'Could not locate the Nextcloud installation (no occ found). Pass -NextcloudRoot.'
}

function Resolve-Ref {
    if ($Ref) { return $Ref }
    try {
        $rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest" -Headers @{ 'User-Agent' = $UserAgent }
        return $rel.tag_name
    } catch {
        Write-Warning 'Could not query the latest release; falling back to the "main" branch.'
        return 'main'
    }
}

function Invoke-Occ {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$OccArgs)
    Push-Location $root
    try {
        if ($DockerContainer) {
            & docker exec -u $WebUser $DockerContainer php occ @OccArgs
        } elseif ($IsWin -or $NoSudo) {
            & php occ @OccArgs
        } else {
            & sudo -u $WebUser php occ @OccArgs
        }
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) {
        throw "occ $($OccArgs -join ' ') failed with exit code $LASTEXITCODE."
    }
}

$root = Resolve-NextcloudRoot
Write-Host "Nextcloud root: $root"

$ref = Resolve-Ref
Write-Host "Installing flutcloud app from ref: $ref"

$temp = Join-Path ([IO.Path]::GetTempPath()) 'flutcloud-install'
New-Item -ItemType Directory -Force -Path $temp | Out-Null

$archiveUrl = if ($ref -match '^v\d') {
    "https://github.com/$Repo/archive/refs/tags/$ref.tar.gz"
} else {
    "https://github.com/$Repo/archive/refs/heads/$ref.tar.gz"
}
$archive = Join-Path $temp 'flutcloud.tar.gz'
$extract = Join-Path $temp 'src'

Write-Host "Downloading $archiveUrl ..."
Invoke-WebRequest -Uri $archiveUrl -OutFile $archive
if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
New-Item -ItemType Directory -Force -Path $extract | Out-Null
& tar -xzf $archive -C $extract
if ($LASTEXITCODE -ne 0) { throw 'Failed to extract the archive (tar).' }

$appSource = Get-ChildItem $extract -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'flutcloud-app') } |
    ForEach-Object { Join-Path $_.FullName 'flutcloud-app' } |
    Select-Object -First 1
if (-not $appSource) { throw 'flutcloud-app folder not found in the downloaded archive.' }

$dest = Join-Path $root 'apps' 'flutcloud'
Write-Host "Installing app to $dest ..."
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Copy-Item -Path (Join-Path $appSource '*') -Destination $dest -Recurse -Force

if ($Composer) {
    if (Get-Command composer -ErrorAction SilentlyContinue) {
        Write-Host 'Generating the Composer autoloader ...'
        Push-Location $dest
        try {
            & composer install --no-dev
            if ($LASTEXITCODE -ne 0) { throw 'composer install failed.' }
        } finally {
            Pop-Location
        }
    } else {
        Write-Warning 'composer not found; skipping autoloader generation (not required).'
    }
}

if ($IsLinuxHost -and -not $DockerContainer) {
    $ownerArgs = @('chown', '-R', "$WebUser`:$WebUser", $dest)
    if ($NoSudo) {
        & chown $ownerArgs
    } else {
        & sudo $ownerArgs
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "chown on $dest failed; the app may still work if $WebUser can read it."
    }
}

Write-Host 'Enabling the app: php occ app:enable flutcloud'
Invoke-Occ app:enable flutcloud

if (-not $SkipVerify) {
    Write-Host 'Verifying the app is enabled ...'
    $output = Invoke-Occ app:list
    if ("$output" -match 'flutcloud') {
        Write-Host 'OK: the flutcloud app is enabled.'
    } else {
        throw 'The flutcloud app did not show up in "occ app:list".'
    }
}

Write-Host "Done. FlutLink can now connect to this server as a FlutCloud instance."
