<#
.SYNOPSIS
  Installs the FlutCloud Nextcloud app on a Nextcloud server.

.DESCRIPTION
  Downloads the FlutCloud Nextcloud app (flutcloud-app.zip) from the latest
  GitHub release of the FlutLink repository (github.com/OseMine/FlutLink) and
  installs it into <nextcloud-root>/apps/flutcloud, then enables the app with
  occ and verifies it. Run this on the machine that hosts the Nextcloud server
  (Linux or Windows host, VM) or on the Docker host using -DockerContainer.

  When piped directly into `iex`, the script runs with default parameters
  (auto-detected Nextcloud root, latest release). Save it to a file first to
  pass parameters.

.PARAMETER NextcloudRoot
  Path to the Nextcloud installation (the folder containing occ). Auto-detected
  otherwise: $env:NEXTCLOUD_ROOT, the current directory, then common locations.
  When run interactively (not piped) the script asks you to confirm the
  detected path or to enter the path where you installed Nextcloud.

.PARAMETER Ref
  Install a specific ref instead of the latest release: a release tag such as
  "v1.0.0" fetches its flutcloud-app.zip release asset (falling back to the
  tagged repository sources when the asset is missing), a branch name fetches
  the current branch sources. Defaults to the latest release.

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
    $detected = $null
    if ($env:NEXTCLOUD_ROOT) {
        if (Test-Path (Join-Path $env:NEXTCLOUD_ROOT 'occ')) {
            $detected = (Get-Item $env:NEXTCLOUD_ROOT).FullName
        } else {
            Write-Warning "NEXTCLOUD_ROOT is set but has no occ; ignoring it."
        }
    }
    $cwd = (Get-Location).Path
    if (-not $detected -and (Test-Path (Join-Path $cwd 'occ'))) { $detected = $cwd }
    $candidates = @(
        '/var/www/nextcloud', '/var/www/html', '/srv/nextcloud',
        '/usr/share/webapps/nextcloud', 'C:\nextcloud', 'C:\htdocs\nextcloud'
    )
    foreach ($c in $candidates) {
        if (-not $detected -and (Test-Path (Join-Path $c 'occ'))) { $detected = (Get-Item $c).FullName }
    }
    if (-not [Console]::IsInputRedirected) {
        if ($detected) {
            while ($true) {
                $ans = Read-Host "Nextcloud installation found at '$detected'. Press Enter to use it, or enter a different path"
                if ([string]::IsNullOrWhiteSpace($ans)) { return $detected }
                if (Test-Path (Join-Path $ans 'occ')) { return (Get-Item $ans).FullName }
                Write-Warning "No occ script found in '$ans'."
            }
        }
        while ($true) {
            $ans = Read-Host 'Could not locate your Nextcloud installation. Enter the path to the folder containing occ (e.g. /var/www/nextcloud)'
            if ([string]::IsNullOrWhiteSpace($ans)) { throw 'No path given; aborting.' }
            if (Test-Path (Join-Path $ans 'occ')) { return (Get-Item $ans).FullName }
            Write-Warning "No occ script found in '$ans'."
        }
    }
    if ($detected) { return $detected }
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

$AssetName = 'flutcloud-app.zip'
$Archive = Join-Path ([IO.Path]::GetTempPath()) 'flutcloud-install\flutcloud-app.zip'

# Downloads the packaged app from a GitHub release; returns $false when the
# asset is missing so the caller can fall back to the repository sources.
function Save-ReleaseZip {
    param([string]$Tag)
    $url = if ($Tag) { "https://github.com/$Repo/releases/download/$Tag/$AssetName" }
           else { "https://github.com/$Repo/releases/latest/download/$AssetName" }
    Write-Host "Downloading $url ..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $Archive -Headers @{ 'User-Agent' = $UserAgent }
        return $true
    } catch {
        Write-Warning 'flutcloud-app.zip is not available there.'
        return $false
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

$temp = Join-Path ([IO.Path]::GetTempPath()) 'flutcloud-install'
New-Item -ItemType Directory -Force -Path $temp | Out-Null

$extract = Join-Path $temp 'src'

# A stale archive from a previous run must not short-circuit the flow.
if (Test-Path $Archive) { Remove-Item -Force $Archive }

$appSource = $null
$fromRelease = $false
if ($Ref -and $Ref -match '^v\d') {
    Write-Host "Installing flutcloud app from release $Ref ..."
    $fromRelease = Save-ReleaseZip $Ref
    if (-not $fromRelease) {
        Write-Warning "Release $Ref has no flutcloud-app.zip; falling back to its repository sources."
    }
} elseif (-not $Ref) {
    Write-Host 'Installing flutcloud app from the latest release ...'
    $fromRelease = Save-ReleaseZip
    if (-not $fromRelease) { Write-Warning 'Falling back to the repository sources.' }
}

if ($fromRelease) {
    # Release zip: the archive root contains the Nextcloud app directly.
    if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    Expand-Archive -Path $Archive -DestinationPath $extract -Force
    if (-not (Test-Path (Join-Path $extract 'appinfo/info.xml'))) {
        throw 'flutcloud-app.zip does not contain appinfo/info.xml.'
    }
    $appSource = $extract
} else {
    $ref = Resolve-Ref
    Write-Host "Installing flutcloud app from ref: $ref"

    $archiveUrl = if ($ref -match '^v\d') {
        "https://github.com/$Repo/archive/refs/tags/$ref.tar.gz"
    } else {
        "https://github.com/$Repo/archive/refs/heads/$ref.tar.gz"
    }
    $tarball = Join-Path $temp 'flutcloud.tar.gz'

    Write-Host "Downloading $archiveUrl ..."
    Invoke-WebRequest -Uri $archiveUrl -OutFile $tarball -Headers @{ 'User-Agent' = $UserAgent }
    if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    & tar -xzf $tarball -C $extract
    if ($LASTEXITCODE -ne 0) { throw 'Failed to extract the archive (tar).' }

    $appSource = Get-ChildItem $extract -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'flutcloud-app') } |
        ForEach-Object { Join-Path $_.FullName 'flutcloud-app' } |
        Select-Object -First 1
    if (-not $appSource) { throw 'flutcloud-app folder not found in the downloaded archive.' }
}

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
Write-Host 'Optional: to also serve the iOS AltStore sources at <server>/ios/{pal,classic}, add the web-server rewrite from flutcloud-app/README.md.'
