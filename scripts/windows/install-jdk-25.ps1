#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Downloads and installs Eclipse Temurin JDK 25 on Windows.
.DESCRIPTION
    Downloads the latest JDK 25 GA build from Adoptium (Eclipse Temurin),
    installs it silently, sets JAVA_HOME, and updates the system PATH.
.EXAMPLE
    .\install-jdk-25.ps1
.EXAMPLE
    .\install-jdk-25.ps1 -InstallDir "D:\Java\jdk-25"
#>

param(
    [string]$InstallDir = "C:\Program Files\Eclipse Adoptium\jdk-25"
)

$ErrorActionPreference = "Stop"

$JdkVersion   = 25
$AdoptiumApi  = "https://api.adoptium.net/v3/binary/latest/$JdkVersion/ga/windows/x64/jdk/hotspot/normal/eclipse"
$TempDir      = Join-Path $env:TEMP "jdk25-install"
$InstallerMsi = Join-Path $TempDir "jdk-25-windows-x64.msi"

function Write-Step([string]$Message) {
    Write-Host "`n[*] $Message" -ForegroundColor Cyan
}

function Write-Success([string]$Message) {
    Write-Host "[+] $Message" -ForegroundColor Green
}

function Write-Failure([string]$Message) {
    Write-Host "[-] $Message" -ForegroundColor Red
}

function Test-JdkAlreadyInstalled {
    $javaExe = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $javaExe) { return $false }

    $versionOutput = & java -version 2>&1 | Select-String -Pattern "version"
    if ($versionOutput -match '"25') {
        Write-Success "JDK 25 is already installed: $versionOutput"
        return $true
    }
    return $false
}

function Get-InstallerFromAdoptium {
    Write-Step "Resolving download URL from Adoptium API..."

    $resolvedUrl = $null
    try {
        $response = Invoke-WebRequest -Uri $AdoptiumApi -Method Head -MaximumRedirection 0 -ErrorAction SilentlyContinue
        $resolvedUrl = $response.Headers.Location
    } catch {
        # Invoke-WebRequest throws on redirect status codes when MaximumRedirection is 0
        if ($_.Exception.Response -and $_.Exception.Response.Headers.Location) {
            $resolvedUrl = $_.Exception.Response.Headers.Location.ToString()
        }
    }

    if (-not $resolvedUrl) {
        $resolvedUrl = $AdoptiumApi
    }

    Write-Host "    URL: $resolvedUrl"

    Write-Step "Downloading JDK 25 installer..."
    New-Item -ItemType Directory -Path $TempDir -Force | Out-Null

    $webClient = New-Object System.Net.WebClient
    $webClient.DownloadFile($resolvedUrl, $InstallerMsi)

    if (-not (Test-Path $InstallerMsi)) {
        throw "Download failed — installer not found at: $InstallerMsi"
    }

    $sizeMb = [math]::Round((Get-Item $InstallerMsi).Length / 1MB, 1)
    Write-Success "Downloaded $sizeMb MB -> $InstallerMsi"
}

function Install-Jdk {
    Write-Step "Installing JDK 25 (silent MSI install)..."

    $msiArgs = @(
        "/i", $InstallerMsi,
        "/qn",
        "/norestart",
        "INSTALLDIR=`"$InstallDir`""
    )

    $process = Start-Process -FilePath "msiexec.exe" -ArgumentList $msiArgs -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "MSI installer exited with code $($process.ExitCode)"
    }

    Write-Success "JDK 25 installed to: $InstallDir"
}

function Set-JavaHome {
    Write-Step "Setting JAVA_HOME and updating PATH..."

    $jdkBin = Join-Path $InstallDir "bin"

    [Environment]::SetEnvironmentVariable("JAVA_HOME", $InstallDir, "Machine")
    Write-Success "JAVA_HOME = $InstallDir"

    $currentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $pathEntries = $currentPath -split ";" | Where-Object { $_ -notmatch "\\jdk" -and $_ -notmatch "\\java" }
    $newPath = ($jdkBin + ";" + ($pathEntries -join ";")).TrimEnd(";")

    [Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")
    Write-Success "PATH updated — $jdkBin placed first"

    $env:JAVA_HOME = $InstallDir
    $env:Path = $newPath
}

function Test-Installation {
    Write-Step "Verifying installation..."

    $javaExe = Join-Path $InstallDir "bin\java.exe"
    if (-not (Test-Path $javaExe)) {
        throw "java.exe not found at: $javaExe"
    }

    $versionOutput = & $javaExe -version 2>&1
    Write-Success "java -version output:`n    $($versionOutput -join "`n    ")"
}

function Remove-TempFiles {
    if (Test-Path $TempDir) {
        Remove-Item -Path $TempDir -Recurse -Force
        Write-Success "Cleaned up temporary files"
    }
}

# ── Main ──────────────────────────────────────────────────────────────────────

Write-Host "`nJDK 25 Installer for Windows" -ForegroundColor Yellow
Write-Host "============================`n" -ForegroundColor Yellow

if (Test-JdkAlreadyInstalled) {
    Write-Host "`nJDK 25 is already present. Nothing to do." -ForegroundColor Yellow
    exit 0
}

try {
    Get-InstallerFromAdoptium
    Install-Jdk
    Set-JavaHome
    Test-Installation
    Remove-TempFiles

    Write-Host "`nJDK 25 installed successfully." -ForegroundColor Green
    Write-Host "Open a new terminal for PATH changes to take effect.`n" -ForegroundColor Yellow
} catch {
    Write-Failure "Installation failed: $_"
    Remove-TempFiles
    exit 1
}
