#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/env.sh"

OUTDIR="${1:-/tmp/duoscreen-shots}"
mkdir -p "$OUTDIR"

TS=$(date +%s)
PHONE_PNG="$OUTDIR/phone-$TS.png"
DHU_PNG="$OUTDIR/dhu-$TS.png"

echo "Capturing phone screenshot..."
"$ADB" -s "$DEVICE_SERIAL" exec-out screencap -p > "$PHONE_PNG"
echo "Phone screenshot saved: $PHONE_PNG"

echo "Capturing DHU screenshot..."
WINDOW_INFO=$(osascript -e '
tell application "System Events"
    set dhuProcs to (every process whose name contains "desktop-head-unit" or unix id is equal to (do shell script "pgrep -f desktop-head-unit | head -n 1"))
    if (count of dhuProcs) > 0 then
        set dhuProc to item 1 of dhuProcs
        if (count of windows of dhuProc) > 0 then
            set dhuWindow to item 1 of windows of dhuProc
            set {x, y} to position of dhuWindow
            set {w, h} to size of dhuWindow
            return (x as string) & "," & (y as string) & "," & (w as string) & "," & (h as string)
        end if
    end if
    return ""
end tell
')

if [ -n "$WINDOW_INFO" ]; then
    IFS=',' read -r X Y W H <<< "$WINDOW_INFO"
    echo "DHU Window found: x=$X, y=$Y, w=$W, h=$H"
    if ! screencapture -x -o -R"$X,$Y,$W,$H" "$DHU_PNG" 2>/dev/null; then
        echo "Fallback to full display..."
        screencapture -x -o "$DHU_PNG"
    fi
else
    echo "Warning: DHU window not found. Capturing full display..."
    screencapture -x -o "$DHU_PNG"
fi

if [ -f "$DHU_PNG" ]; then
    echo "DHU screenshot saved: $DHU_PNG"
else
    echo "Failed to capture DHU screenshot."
fi
