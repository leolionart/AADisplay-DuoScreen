#!/usr/bin/env bash

export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export ANDROID_HOME="/Users/admin/android-sdk"
export ADB="${ANDROID_HOME}/platform-tools/adb"
export DEVICE_SERIAL="${DEVICE_SERIAL:-$($ADB devices | grep -w "device" | head -n 1 | awk '{print $1}')}"
export DHU="${ANDROID_HOME}/extras/google/auto/desktop-head-unit"
export REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [ ! -x "$ADB" ]; then
    echo "Error: adb not found at $ADB" >&2
    return 1 2>/dev/null || exit 1
fi
