#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

APK_PATH="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH (Run build.sh first)" >&2
    exit 1
fi

TMP_APK="/data/local/tmp/duoscreen.apk"

echo "Pushing APK to device..."
"$ADB" -s "$DEVICE_SERIAL" push "$APK_PATH" "$TMP_APK"

echo "Installing via package manager with fake vending..."
INSTALL_OUT=$("$ADB" -s "$DEVICE_SERIAL" shell pm install -i com.android.vending -r "$TMP_APK" 2>&1)

echo "$INSTALL_OUT"
if echo "$INSTALL_OUT" | grep -q "Success"; then
    echo "Installation: Success"
else
    echo "Installation: Failure"
    exit 1
fi
