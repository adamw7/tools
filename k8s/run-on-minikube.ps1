<#
.SYNOPSIS
    Installs minikube (and kubectl) if missing, then builds and runs the
    project's SampleApp (the CSV column-uniqueness checker) on a local
    minikube cluster on Windows.

.DESCRIPTION
    Windows PowerShell counterpart to k8s/run-on-minikube.sh. End to end it:
      1. Ensures minikube and kubectl are installed (downloads the official
         Windows binaries into $ToolsDir and puts them on this session's PATH
         if they are not already available).
      2. Builds the fat jar (mvn -DskipTests package).
      3. Builds the k8s/Dockerfile deployment image.
      4. Starts minikube (docker driver) and loads the local image.
      5. Applies the ConfigMap and Job, waits for completion, prints the logs.

    Requirements already on the host: docker, a JDK 25 + Maven (or the Maven
    wrapper). Run from the repository root or from k8s/.

.PARAMETER Column
    CSV column to check for uniqueness. Defaults to 'country' (which repeats,
    so the result is NOT unique). Use 'id' for a unique column.

.PARAMETER Image
    Name/tag of the deployment image to build and load. Defaults to
    'tools-k8s:local'.

.PARAMETER ToolsDir
    Directory where minikube/kubectl are downloaded when they are missing.
    Defaults to "$env:LOCALAPPDATA\tools-minikube".

.EXAMPLE
    .\k8s\run-on-minikube.ps1

.EXAMPLE
    .\k8s\run-on-minikube.ps1 -Column id
#>

param(
    [string]$Column = "country",
    [string]$Image = "tools-k8s:local",
    [string]$ToolsDir = (Join-Path $env:LOCALAPPDATA "tools-minikube")
)

$ErrorActionPreference = "Stop"

$ScriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$RootDir   = Split-Path -Parent $ScriptDir
$K8sDir    = Join-Path $RootDir "k8s"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Write-Success([string]$Message) {
    Write-Host "[+] $Message" -ForegroundColor Green
}

function Test-CommandExists([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Save-Binary([string]$Url, [string]$Destination) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $Destination) -Force | Out-Null
    $webClient = New-Object System.Net.WebClient
    $webClient.DownloadFile($Url, $Destination)
    if (-not (Test-Path $Destination)) {
        throw "Download failed — file not found at: $Destination"
    }
}

function Install-Minikube {
    if (Test-CommandExists "minikube") {
        Write-Success "minikube already installed: $((& minikube version --short) 2>$null)"
        return
    }

    Write-Step "Installing minikube (Windows amd64 binary)"
    $minikubeExe = Join-Path $ToolsDir "minikube.exe"
    Save-Binary "https://github.com/kubernetes/minikube/releases/latest/download/minikube-windows-amd64.exe" $minikubeExe
    Write-Success "minikube installed to $minikubeExe"
}

function Install-Kubectl {
    if (Test-CommandExists "kubectl") {
        Write-Success "kubectl already installed"
        return
    }

    Write-Step "Installing kubectl (Windows amd64 binary)"
    $stableVersion = (New-Object System.Net.WebClient).DownloadString(
        "https://dl.k8s.io/release/stable.txt").Trim()
    $kubectlExe = Join-Path $ToolsDir "kubectl.exe"
    Save-Binary "https://dl.k8s.io/release/$stableVersion/bin/windows/amd64/kubectl.exe" $kubectlExe
    Write-Success "kubectl $stableVersion installed to $kubectlExe"
}

function Add-ToolsDirToPath {
    # Put the download dir first on this session's PATH so freshly installed
    # binaries are found without opening a new terminal.
    if (Test-Path $ToolsDir) {
        $env:Path = "$ToolsDir;$env:Path"
    }
}

function Assert-DockerAvailable {
    if (-not (Test-CommandExists "docker")) {
        throw "docker is required but was not found on PATH. Install Docker Desktop and retry."
    }
}

function Invoke-MavenBuild {
    Write-Step "Building the application (mvn -DskipTests package)"
    $mvn = if (Test-Path (Join-Path $RootDir "mvnw.cmd")) { Join-Path $RootDir "mvnw.cmd" } else { "mvn" }
    & $mvn -B -ntp -DskipTests package -f (Join-Path $RootDir "pom.xml")
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit code $LASTEXITCODE)" }
}

function Build-DockerImage {
    Write-Step "Building the Docker image: $Image"
    & docker build -f (Join-Path $K8sDir "Dockerfile") -t $Image $RootDir
    if ($LASTEXITCODE -ne 0) { throw "docker build failed (exit code $LASTEXITCODE)" }
}

function Start-Minikube {
    Write-Step "Ensuring minikube is running"
    & minikube status *> $null
    if ($LASTEXITCODE -ne 0) {
        & minikube start --driver=docker
        if ($LASTEXITCODE -ne 0) { throw "minikube start failed (exit code $LASTEXITCODE)" }
    }

    Write-Step "Loading $Image into minikube"
    & minikube image load $Image
    if ($LASTEXITCODE -ne 0) { throw "minikube image load failed (exit code $LASTEXITCODE)" }
}

function Invoke-Job {
    Write-Step "Applying manifests"
    & kubectl apply -f (Join-Path $K8sDir "configmap-sample-data.yaml")

    # Recreate the Job so repeated runs pick up new data/image.
    & kubectl delete job tools-uniqueness-check --ignore-not-found

    # Substitute the target column (default 'country') into the manifest.
    $jobManifest = Get-Content (Join-Path $K8sDir "job-uniqueness-check.yaml") -Raw
    $jobManifest = $jobManifest.Replace('"country"', "`"$Column`"")
    $jobManifest | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw "kubectl apply of the Job failed (exit code $LASTEXITCODE)" }

    Write-Step "Waiting for the Job to complete"
    & kubectl wait --for=condition=complete --timeout=120s job/tools-uniqueness-check
    if ($LASTEXITCODE -ne 0) {
        & kubectl wait --for=condition=failed --timeout=10s job/tools-uniqueness-check
    }

    Write-Step "Job logs"
    & kubectl logs -l app.kubernetes.io/component=uniqueness-check --tail=-1
}

# -- Main ----------------------------------------------------------------------

Write-Host "`nRun SampleApp on minikube (Windows)" -ForegroundColor Yellow
Write-Host   "===================================" -ForegroundColor Yellow

Assert-DockerAvailable
Install-Minikube
Install-Kubectl
Add-ToolsDirToPath
Invoke-MavenBuild
Build-DockerImage
Start-Minikube
Invoke-Job

Write-Host "`nDone." -ForegroundColor Green
