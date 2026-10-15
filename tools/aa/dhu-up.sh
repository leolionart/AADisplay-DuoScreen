#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

echo "Checking if DHU is already running..."
if pgrep -f "desktop-head-unit" > /dev/null; then
    echo "DHU is already running. Skipping startup."
else
    echo "Starting Head Unit Server on device..."
    # Open AA Settings
    "$ADB" -s "$DEVICE_SERIAL" shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity
    
    # Wait for activity to render
    sleep 2
    
    # Allow overriding coords via env vars
    OVERFLOW_X=${AA_OVERFLOW_X:-1007}
    OVERFLOW_Y=${AA_OVERFLOW_Y:-168}
    HEAD_UNIT_SERVER_X=${AA_SERVER_X:-666}
    HEAD_UNIT_SERVER_Y=${AA_SERVER_Y:-580}

    echo "Tapping overflow menu ($OVERFLOW_X, $OVERFLOW_Y)..."
    "$ADB" -s "$DEVICE_SERIAL" shell input tap $OVERFLOW_X $OVERFLOW_Y
    sleep 1

    echo "Tapping 'Start head unit server' ($HEAD_UNIT_SERVER_X, $HEAD_UNIT_SERVER_Y)..."
    "$ADB" -s "$DEVICE_SERIAL" shell input tap $HEAD_UNIT_SERVER_X $HEAD_UNIT_SERVER_Y
    sleep 1

    echo "Starting Desktop Head Unit (DHU)..."
    nohup "$DHU" -u "$DEVICE_SERIAL" -i touch > /dev/null 2>&1 &
    
    sleep 2
    echo "DHU launched."
fi

echo "Forwarding DHU port 5277..."
"$ADB" -s "$DEVICE_SERIAL" forward tcp:5277 tcp:5277

echo "Verifying port forward..."
"$ADB" -s "$DEVICE_SERIAL" forward --list | grep "tcp:5277" || echo "Warning: Port 5277 might not be forwarded."
