#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

cd "$REPO_ROOT"
echo "Building debug APK..."
./gradlew assembleDebug

APK_PATH="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH" >&2
    exit 1
fi
echo "APK built successfully: $APK_PATH"
