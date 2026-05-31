#!/bin/bash
if command -v apt-get &>/dev/null; then
    sudo apt-get update && sudo apt-get install --only-upgrade git
elif command -v dnf &>/dev/null; then
    sudo dnf upgrade git
elif command -v yum &>/dev/null; then
    sudo yum update git
elif command -v pacman &>/dev/null; then
    sudo pacman -Syu git
else
    echo "Unsupported package manager" >&2
    exit 1
fi
