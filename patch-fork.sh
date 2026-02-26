#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PATCH="$SCRIPT_DIR/emudeck.patch"

red()   { printf '\033[1;31m%s\033[0m\n' "$*"; }
green() { printf '\033[1;32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }

if [[ ! -f "$PATCH" ]]; then
    red "Patch not found: $PATCH"
    exit 1
fi

cd "$SCRIPT_DIR"

bold "Applying storage-onboarding.patch..."
if git am "$PATCH"; then
    green "Patch applied successfully."
else
    red "Conflicts detected. Resolve them, then run:"
    echo "  git am --continue"
    echo ""
    echo "Or abort with:"
    echo "  git am --abort"
    exit 1
fi
