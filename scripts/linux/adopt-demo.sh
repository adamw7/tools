#!/bin/bash
# Records a demonstration of the Claude Code adoption pipeline on Linux.
#
# Runs `adopt` with --dry-run, so the pipeline clones, branches, generates a
# CLAUDE.md, wires the build guard in and verifies it, but never pushes and never
# opens a pull request — the demo is safe to record against a repository somebody
# else owns. A dry run also needs no `gh` and no GitHub credentials.
#
# The adoption runs exactly once, however many recordings are made of it: the
# session is captured as a plain-text transcript with script(1), and as an
# asciinema cast around it when asciinema is installed. The transcript is then
# rendered as an .mpg video where ffmpeg and Pillow are available — see
# adopt-demo-video.py, which does that rendering and can be run on its own.
#
# Usage: ./adopt-demo.sh [github-repo-url] [output-directory]
# Example: ./adopt-demo.sh https://github.com/octocat/Spoon-Knife.git /tmp/demo
#
# The default repository is small, public, and carries enough real code for
# `claude init` to have something to document — an empty one it declines to write a
# CLAUDE.md for, which costs the run every claude-init attempt before it gives up.
# It is also not a Maven project: the Maven guard wires in a released
# claude-code-enforcer, which a SNAPSHOT build of tools has none of, so a Maven
# repository cannot be demonstrated from an unreleased checkout.

set -euo pipefail

# The repository root and this script's own path, so the demo runs the same from
# any directory and can re-enter itself under a recorder.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SELF="${SCRIPT_DIR}/$(basename -- "${BASH_SOURCE[0]}")"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

# The recorders re-enter this script rather than take the adoption as a quoted
# string, so its arguments are settled once. They reach the re-entered run
# through the environment, the command line already carrying the mode.
MODE="${1:-}"
REPO_URL="${ADOPT_DEMO_REPO_URL:-}"
OUTPUT_DIR="${ADOPT_DEMO_OUTPUT_DIR:-}"
if [[ -z "${MODE}" || "${MODE}" != --*-only ]]; then
    REPO_URL="${1:-https://github.com/sindresorhus/is-online.git}"
    OUTPUT_DIR="${2:-${PWD}/target/adopt-demo}"
fi

if [[ -z "${OUTPUT_DIR}" ]]; then
    echo "[-] ${MODE} is the recorders' own entry point and is not meant to be run directly." >&2
    exit 2
fi

TRANSCRIPT="${OUTPUT_DIR}/adopt-demo.txt"
CAST="${OUTPUT_DIR}/adopt-demo.cast"
VIDEO="${OUTPUT_DIR}/adopt-demo.mpg"
REPORT="${OUTPUT_DIR}/adopt-demo-report.json"
WORKSPACE="${OUTPUT_DIR}/workspace"
# Written by the recorded run and read once it is over: asciinema exits zero
# whatever it recorded, so a failed adoption would otherwise be reported as a
# successful demo.
STATUS_FILE="${OUTPUT_DIR}/adoption-status"
TIMEOUT_MINUTES="${ADOPT_DEMO_TIMEOUT_MINUTES:-5}"

export ADOPT_DEMO_REPO_URL="${REPO_URL}"
export ADOPT_DEMO_OUTPUT_DIR="${OUTPUT_DIR}"

step()    { echo -e "\n[*] $1"; }
success() { echo "[+] $1"; }
failure() { echo "[-] $1" >&2; }

require_tools() {
    step "Checking the demo's own prerequisites..."
    local missing=()
    local tool
    for tool in git claude mvn; do
        if ! command -v "${tool}" &>/dev/null; then
            missing+=("${tool}")
        fi
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
        failure "Not on the PATH: ${missing[*]}"
        failure "A dry run shells out to git and claude; mvn launches the pipeline."
        exit 1
    fi
    success "git, claude and mvn are all present"
    success "gh is not needed: a dry run opens no pull request"
}

build_adopt_module() {
    step "Building the adopt module and what it depends on..."
    mvn -q -f "${PROJECT_ROOT}/pom.xml" -pl adopt -am install -DskipTests
    success "tools.adopt is installed in the local repository"
}

prepare_output_directory() {
    step "Preparing ${OUTPUT_DIR}..."
    rm -rf "${OUTPUT_DIR}"
    mkdir -p "${WORKSPACE}"
    success "Output directory is empty, so it holds this run alone"
}

# The adoption itself, launched through exec:java so Maven puts the module's full
# runtime classpath on the command. -B drops the ANSI colouring a recording would
# otherwise keep as escape sequences, and -q leaves Maven's own lifecycle chatter
# out, so what is recorded is the pipeline's narration and nothing else.
run_adoption() {
    mvn -B -q -f "${PROJECT_ROOT}/pom.xml" -pl adopt exec:java \
        -Dexec.args="${REPO_URL} --workspace ${WORKSPACE} --dry-run --assets --timeout ${TIMEOUT_MINUTES} --report ${REPORT}"
}

# script(1) keeps both of the pipeline's streams in the order a terminal shows
# them, which redirecting them separately does not, and --return makes the
# adoption's own exit code the recording's.
record_transcript() {
    script --quiet --return --command "${SELF} --adoption-only" "${TRANSCRIPT}"
}

report_outcome() {
    step "The run's report:"
    if [[ -f "${REPORT}" ]]; then
        sed 's/^/    /' "${REPORT}"
    else
        failure "No report at ${REPORT} — the run stopped before it could be written"
    fi
}

report_recordings() {
    success "Transcript: ${TRANSCRIPT}"
    if [[ -f "${CAST}" ]]; then
        success "Cast:       ${CAST} (replay with 'asciinema play ${CAST}')"
    fi
    if [[ -f "${VIDEO}" ]]; then
        success "Video:      ${VIDEO}"
    fi
}

# Renders the transcript as an .mpg, when the machine has what that takes. The
# video is a convenience over the recording rather than a second recording, so a
# machine without ffmpeg or Pillow is told what is missing and loses nothing else.
render_video() {
    if [[ ! -f "${TRANSCRIPT}" ]]; then
        return 0
    fi
    if ! command -v ffmpeg &>/dev/null; then
        success "ffmpeg is not installed; skipping the video"
        return 0
    fi
    if ! python3 -c "import PIL" &>/dev/null; then
        success "Pillow is not installed (pip install Pillow); skipping the video"
        return 0
    fi
    step "Rendering the transcript as an MPEG video..."
    # Non-fatal: the recording is the deliverable, and it is already written.
    python3 "${SCRIPT_DIR}/adopt-demo-video.py" --transcript "${TRANSCRIPT}" --output "${VIDEO}" \
        || failure "The video could not be rendered; the transcript is unaffected"
}

case "${MODE}" in
    --adoption-only)
        adoption_status=0
        run_adoption || adoption_status=$?
        echo "${adoption_status}" > "${STATUS_FILE}"
        exit "${adoption_status}"
        ;;
    --transcript-only)
        record_transcript
        exit
        ;;
esac

echo -e "\nClaude Code adoption — recorded dry run"
printf "======================================\n\n"
echo "Repository: ${REPO_URL}"
echo "Output:     ${OUTPUT_DIR}"

require_tools
build_adopt_module
prepare_output_directory

step "Recording the dry run against ${REPO_URL}..."
# The adoption's exit code is kept rather than ending the demo here: a run that
# failed part-way still wrote a report, and the report is the thing to look at.
if command -v asciinema &>/dev/null; then
    asciinema rec --overwrite --command "${SELF} --transcript-only" "${CAST}" || true
else
    success "asciinema is not installed; the text transcript is the recording"
    "${SELF}" --transcript-only || true
fi

# A missing status file means the adoption never got as far as writing one.
adoption_status="$(cat "${STATUS_FILE}" 2>/dev/null || echo 1)"

render_video
report_recordings
report_outcome

if [[ ${adoption_status} -eq 0 ]]; then
    echo -e "\nDemo complete. Nothing was pushed and no pull request was opened."
else
    failure "The adoption exited ${adoption_status}. The transcript above says where it stopped."
fi
echo "The checkout a dry run leaves behind is under ${WORKSPACE}."
exit "${adoption_status}"
