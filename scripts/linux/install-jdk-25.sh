#!/bin/bash
# Downloads and installs Eclipse Temurin JDK 25 on Linux.
# Usage: ./install-jdk-25.sh [install-dir]
# Example: ./install-jdk-25.sh /opt/jdk-25

set -euo pipefail

INSTALL_DIR="${1:-/opt/eclipse-adoptium/jdk-25}"
JDK_VERSION=25
ADOPTIUM_API="https://api.adoptium.net/v3/binary/latest/${JDK_VERSION}/ga/linux/x64/jdk/hotspot/normal/eclipse"
TEMP_DIR="/tmp/jdk25-install"
ARCHIVE_PATH="${TEMP_DIR}/jdk-25-linux-x64.tar.gz"

step()    { echo -e "\n[*] $1"; }
success() { echo "[+] $1"; }
failure() { echo "[-] $1" >&2; }

test_jdk_already_installed() {
    if ! command -v java &>/dev/null; then
        return 1
    fi
    if java -version 2>&1 | grep -q '"25'; then
        success "JDK 25 is already installed: $(java -version 2>&1 | head -1)"
        return 0
    fi
    return 1
}

download_from_adoptium() {
    step "Resolving download URL from Adoptium API..."
    local resolved_url
    resolved_url=$(curl -Ls -o /dev/null -w '%{url_effective}' "$ADOPTIUM_API")
    echo "    URL: $resolved_url"

    step "Downloading JDK 25 archive..."
    mkdir -p "$TEMP_DIR"
    curl -L --progress-bar -o "$ARCHIVE_PATH" "$resolved_url"

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

    sudo tee "$profile_script" > /dev/null <<EOF
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
printf "==========================\n\n"

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

download_from_adoptium
install_jdk
set_java_home
test_installation
remove_temp_files

echo -e "\nJDK 25 installed successfully."
echo "Run 'source /etc/profile.d/jdk25.sh' or open a new terminal for PATH changes to take effect."
