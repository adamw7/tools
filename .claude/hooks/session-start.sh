#!/bin/bash
# CLAUDE_ENV_FILE and CLAUDE_PROJECT_DIR are provided by the Claude Code runtime.
# shellcheck disable=SC2154
set -euo pipefail

if [[ "${CLAUDE_CODE_REMOTE:-}" != "true" ]]; then
  exit 0
fi

# Install Java 25 if not already present
if ! find /usr/lib/jvm -maxdepth 1 -name "java-25-openjdk*" -type d | grep -q .; then
  apt-get update -q
  apt-get install -y -q openjdk-25-jdk
fi

JAVA_HOME_25=$(find /usr/lib/jvm -maxdepth 1 -name "java-25-openjdk*" -type d | head -1)

# SessionStart fires on resume as well as startup, and a resumed session can land
# in a re-provisioned container with no JDK 25, so the hook has to keep doing this
# work rather than run once. Every step below is therefore idempotent: appending
# the exports again would grow the env file by two lines on each resume.
if ! grep -qs "^export JAVA_HOME=${JAVA_HOME_25}$" "${CLAUDE_ENV_FILE}"; then
  {
    echo "export JAVA_HOME=${JAVA_HOME_25}"
    echo "export PATH=${JAVA_HOME_25}/bin:\$PATH"
  } >> "${CLAUDE_ENV_FILE}"
fi

# The exports are written before the slow warm below, so a hook killed by its
# timeout still leaves later tool calls with a JDK on the PATH.
export JAVA_HOME="${JAVA_HOME_25}"
export PATH="${JAVA_HOME_25}/bin:${PATH}"

# Pre-download all Maven dependencies to local repo for faster builds. The marker
# sits in the local repository the warm fills, so it is absent exactly when the
# repository needs filling: a resume in the same container skips a warm that has
# already happened, and a re-provisioned container does it again. A warm that
# fails leaves no marker and is retried rather than recorded as done.
warmed_marker="${HOME}/.m2/.tools-dependencies-warmed"
if [[ ! -f "${warmed_marker}" ]]; then
  cd "${CLAUDE_PROJECT_DIR}"
  mvn dependency:go-offline -q
  mkdir -p "$(dirname "${warmed_marker}")"
  touch "${warmed_marker}"
fi
