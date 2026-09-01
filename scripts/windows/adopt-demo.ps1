<#
.SYNOPSIS
    Records a demonstration of the Claude Code adoption pipeline on Windows.
.DESCRIPTION
    Runs `adopt` with --dry-run, so the pipeline clones, branches, generates a
    CLAUDE.md, wires the build guard in and verifies it, but never pushes and
    never opens a pull request — the demo is safe to record against a repository
    somebody else owns. A dry run also needs no gh and no GitHub credentials.

    The session is captured with Start-Transcript, the Windows counterpart of the
    script(1) recording the Linux variant makes.

    The default repository is small, public, and carries enough real code for
    `claude init` to have something to document — an empty one it declines to
    write a CLAUDE.md for, which costs the run every claude-init attempt before
    it gives up. It is also not a Maven project: the Maven guard wires in a
    released claude-code-enforcer, which a SNAPSHOT build of tools has none of,
    so a Maven repository cannot be demonstrated from an unreleased checkout.
.EXAMPLE
    .\adopt-demo.ps1
.EXAMPLE
    .\adopt-demo.ps1 -RepoUrl "https://github.com/octocat/Spoon-Knife.git" -OutputDir "C:\Temp\demo"
#>

param(
    [string]$RepoUrl = "https://github.com/sindresorhus/is-online.git",
    [string]$OutputDir = (Join-Path $PWD "target\adopt-demo"),
    [int]$TimeoutMinutes = 5
)

$ErrorActionPreference = "Stop"

$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$Transcript  = Join-Path $OutputDir "adopt-demo.txt"
$Report      = Join-Path $OutputDir "adopt-demo-report.json"
$Workspace   = Join-Path $OutputDir "workspace"

function Write-Step([string]$Message) {
    Write-Host "`n[*] $Message" -ForegroundColor Cyan
}

function Write-Success([string]$Message) {
    Write-Host "[+] $Message" -ForegroundColor Green
}

function Write-Failure([string]$Message) {
    Write-Host "[-] $Message" -ForegroundColor Red
}

function Test-Prerequisites {
    Write-Step "Checking the demo's own prerequisites..."
    $missing = @()
    foreach ($tool in @("git", "claude", "mvn")) {
        if ($null -eq (Get-Command $tool -ErrorAction SilentlyContinue)) {
            $missing += $tool
        }
    }
    if ($missing.Count -gt 0) {
        Write-Failure "Not on the PATH: $($missing -join ', ')"
        Write-Failure "A dry run shells out to git and claude; mvn launches the pipeline."
        exit 1
    }
    Write-Success "git, claude and mvn are all present"
    Write-Success "gh is not needed: a dry run opens no pull request"
}

function Build-AdoptModule {
    Write-Step "Building the adopt module and what it depends on..."
    mvn -q -f (Join-Path $ProjectRoot "pom.xml") -pl adopt -am install "-DskipTests" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "The adopt module did not build" }
    Write-Success "tools.adopt is installed in the local repository"
}

function Initialize-OutputDirectory {
    Write-Step "Preparing $OutputDir..."
    if (Test-Path $OutputDir) { Remove-Item -Recurse -Force $OutputDir }
    New-Item -ItemType Directory -Force -Path $Workspace | Out-Null
    Write-Success "Output directory is empty, so it holds this run alone"
}

# The adoption itself, launched through exec:java so Maven puts the module's full
# runtime classpath on the command. -B drops the ANSI colouring a recording would
# otherwise keep as escape sequences, and -q leaves Maven's own lifecycle chatter
# out, so what is recorded is the pipeline's narration and nothing else.
function Invoke-Adoption {
    $execArgs = "$RepoUrl --workspace $Workspace --dry-run --assets " +
                "--timeout $TimeoutMinutes --report $Report"
    # Out-Host keeps Maven's output on the console — where Start-Transcript still
    # records it — and off this function's return value: a PowerShell function
    # returns everything left on the success stream, so without it the exit code
    # would come back wrapped in every line the pipeline printed.
    mvn -B -q -f (Join-Path $ProjectRoot "pom.xml") -pl adopt exec:java "-Dexec.args=$execArgs" | Out-Host
    return $LASTEXITCODE
}

function Show-Report {
    Write-Step "The run's report:"
    if (Test-Path $Report) {
        Get-Content $Report | ForEach-Object { "    $_" }
    } else {
        Write-Failure "No report at $Report — the run stopped before it could be written"
    }
}

Write-Host "`nClaude Code adoption - recorded dry run"
Write-Host "======================================`n"
Write-Host "Repository: $RepoUrl"
Write-Host "Output:     $OutputDir"

Test-Prerequisites
Build-AdoptModule
Initialize-OutputDirectory

Write-Step "Recording the dry run against $RepoUrl..."
Start-Transcript -Path $Transcript -Force | Out-Null
# The adoption's exit code is kept rather than ending the demo here: a run that
# failed part-way still wrote a report, and the report is the thing to look at.
$adoptionStatus = 0
try {
    $adoptionStatus = Invoke-Adoption
} finally {
    Stop-Transcript | Out-Null
}

Write-Success "Transcript: $Transcript"
Show-Report

if ($adoptionStatus -eq 0) {
    Write-Host "`nDemo complete. Nothing was pushed and no pull request was opened."
} else {
    Write-Failure "The adoption exited $adoptionStatus. The transcript above says where it stopped."
}
Write-Host "The checkout a dry run leaves behind is under $Workspace."
exit $adoptionStatus
