#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for dir in "$SCRIPT_DIR"/*/; do
    if [ -e "$dir/.git" ]; then
        (cd "$dir" && git pull) &
    fi
done
wait
