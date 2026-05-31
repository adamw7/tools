#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# Install Java 21 if not already present
if ! find /usr/lib/jvm -maxdepth 1 -name "java-21-openjdk*" -type d | grep -q .; then
  apt-get update -q
  apt-get install -y -q openjdk-21-jdk
fi

JAVA_HOME_21=$(find /usr/lib/jvm -maxdepth 1 -name "java-21-openjdk*" -type d | head -1)

echo "export JAVA_HOME=${JAVA_HOME_21}" >> "$CLAUDE_ENV_FILE"
echo "export PATH=${JAVA_HOME_21}/bin:\$PATH" >> "$CLAUDE_ENV_FILE"

export JAVA_HOME="${JAVA_HOME_21}"
export PATH="${JAVA_HOME_21}/bin:$PATH"

# Pre-download all Maven dependencies to local repo for faster builds
cd "${CLAUDE_PROJECT_DIR}"
mvn dependency:go-offline -q
