#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Install JDK 25 if not already present
JDK_25_HOME="/opt/eclipse-adoptium/jdk-25"
if [ ! -d "${JDK_25_HOME}" ]; then
  bash "${CLAUDE_PROJECT_DIR}/scripts/linux/install-jdk-25.sh" "${JDK_25_HOME}"
fi

echo "export JAVA_HOME=${JDK_25_HOME}" >> "$CLAUDE_ENV_FILE"
echo "export PATH=${JDK_25_HOME}/bin:\$PATH" >> "$CLAUDE_ENV_FILE"

export JAVA_HOME="${JDK_25_HOME}"
export PATH="${JDK_25_HOME}/bin:$PATH"

# Pre-download all Maven dependencies to local repo for faster builds
cd "${CLAUDE_PROJECT_DIR}"
mvn dependency:go-offline -q
