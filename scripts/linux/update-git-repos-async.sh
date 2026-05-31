#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for dir in "$SCRIPT_DIR"/*/; do
    if [ -d "$dir/.git" ]; then
        (cd "$dir" && git pull) &
    fi
done
