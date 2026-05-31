#!/bin/bash
# Downloads and installs Eclipse Temurin JDK 25 on Linux.
# Usage: ./install-jdk-25.sh [install-dir]
# Example: ./install-jdk-25.sh /opt/jdk-25

set -euo pipefail

INSTALL_DIR="${1:-/opt/eclipse-adoptium/jdk-25}"
GITHUB_RELEASES="https://github.com/adoptium/temurin25-binaries/releases"
TEMP_DIR="/tmp/jdk25-install"
ARCHIVE_PATH="${TEMP_DIR}/jdk-25-linux-x64.tar.gz"

step()    { echo -e "\n[*] $1"; }
success() { echo "[+] $1"; }
failure() { echo "[-] $1" >&2; }

test_jdk_already_installed() {
    local java_exe="${INSTALL_DIR}/bin/java"
    if [ ! -x "$java_exe" ]; then
        return 1
    fi
    if "$java_exe" -version 2>&1 | grep -q '"25'; then
        success "JDK 25 is already installed at: ${INSTALL_DIR}"
        return 0
    fi
    return 1
}

resolve_download_url() {
    step "Resolving latest JDK 25 release from GitHub..."
    local latest_tag
    latest_tag=$(curl -sI --max-time 10 "${GITHUB_RELEASES}/latest" \
        | grep -i "^location:" | grep -o 'jdk-[^[:space:]]*' | head -1)

    if [ -z "$latest_tag" ]; then
        failure "Could not resolve latest release tag from GitHub"
        exit 1
    fi

    local version_clean="${latest_tag#jdk-}"
    local version_safe="${version_clean//+/_}"
    local version_encoded="${version_clean//+/%2B}"
    local filename="OpenJDK25U-jdk_x64_linux_hotspot_${version_safe}.tar.gz"

    echo "    Tag: ${latest_tag}"
    echo "    File: ${filename}"
    echo "${GITHUB_RELEASES}/download/jdk-${version_encoded}/${filename}"
}

download_jdk() {
    local download_url
    download_url=$(resolve_download_url)
    local url
    url=$(echo "$download_url" | tail -1)

    step "Downloading JDK 25 archive..."
    mkdir -p "$TEMP_DIR"
    curl -L --progress-bar -o "$ARCHIVE_PATH" "$url"

    local size_mb
    size_mb=$(du -m "$ARCHIVE_PATH" | cut -f1)
    success "Downloaded ${size_mb} MB -> $ARCHIVE_PATH"
}

install_jdk() {
    step "Installing JDK 25..."
    mkdir -p "$INSTALL_DIR"
    tar -xzf "$ARCHIVE_PATH" -C "$INSTALL_DIR" --strip-components=1
    success "JDK 25 installed to: $INSTALL_DIR"
}

set_java_home() {
    step "Setting JAVA_HOME and updating PATH..."
    local profile_script="/etc/profile.d/jdk25.sh"

    tee "$profile_script" > /dev/null <<EOF
export JAVA_HOME="${INSTALL_DIR}"
export PATH="\${JAVA_HOME}/bin:\${PATH}"
EOF

    export JAVA_HOME="$INSTALL_DIR"
    export PATH="${JAVA_HOME}/bin:${PATH}"

    success "JAVA_HOME = $INSTALL_DIR"
    success "Profile script written to $profile_script"
}

test_installation() {
    step "Verifying installation..."
    local java_exe="${INSTALL_DIR}/bin/java"
    if [ ! -x "$java_exe" ]; then
        failure "java binary not found at: $java_exe"
        exit 1
    fi
    success "java -version output:"
    "$java_exe" -version 2>&1 | sed 's/^/    /'
}

remove_temp_files() {
    if [ -d "$TEMP_DIR" ]; then
        rm -rf "$TEMP_DIR"
        success "Cleaned up temporary files"
    fi
}

# ── Main ──────────────────────────────────────────────────────────────────────

echo -e "\nJDK 25 Installer for Linux"
echo "==========================\n"

if test_jdk_already_installed; then
    echo -e "\nJDK 25 is already present. Nothing to do."
    exit 0
fi

cleanup_on_failure() {
    failure "Installation failed: $1"
    remove_temp_files
    exit 1
}

trap 'cleanup_on_failure "unexpected error"' ERR

download_jdk
install_jdk
set_java_home
test_installation
remove_temp_files

echo -e "\nJDK 25 installed successfully."
echo "Run 'source /etc/profile.d/jdk25.sh' or open a new terminal for PATH changes to take effect."
