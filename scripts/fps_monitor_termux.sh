#!/bin/sh
# Android Termux / Local Shell Direct FPS Monitor
# Usage: sh fps_monitor_termux.sh [surface_name]

TARGET_SURFACE="$1"

echo "=== Android Local FPS Monitor (Termux / Root) ==="
echo "Initializing..."

# Check if running as root
IS_ROOT=0
if [ "$(id -u)" -eq 0 ]; then
    IS_ROOT=1
elif which su >/dev/null 2>&1; then
    IS_ROOT=2
fi

get_latency() {
    local surface="$1"
    if [ "$IS_ROOT" -eq 1 ]; then
        if [ -n "$surface" ]; then
            dumpsys SurfaceFlinger --latency "$surface"
        else
            dumpsys SurfaceFlinger --latency
        fi
    elif [ "$IS_ROOT" -eq 2 ]; then
        if [ -n "$surface" ]; then
            su -c "dumpsys SurfaceFlinger --latency \"$surface\""
        else
            su -c "dumpsys SurfaceFlinger --latency"
        fi
    else
        if [ -n "$surface" ]; then
            dumpsys SurfaceFlinger --latency "$surface" 2>/dev/null
        else
            dumpsys SurfaceFlinger --latency 2>/dev/null
        fi
    fi
}

get_focused_window() {
    if [ "$IS_ROOT" -eq 2 ]; then
        su -c "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'" | head -n 1
    else
        dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -n 1
    fi
}

LAST_PRESENT_TIME=0

while true; do
    if [ -z "$TARGET_SURFACE" ]; then
        FOCUS_INFO=$(get_focused_window)
        CUR_SURFACE=$(echo "$FOCUS_INFO" | sed -n 's/.*Window{[^ ]* [^ ]* \([^}]*\)}.*/\1/p')
    else
        CUR_SURFACE="$TARGET_SURFACE"
    fi

    RAW_DATA=$(get_latency "$CUR_SURFACE")
    
    # Process latency lines
    if [ -n "$RAW_DATA" ]; then
        REFRESH_NS=$(echo "$RAW_DATA" | head -n 1)
        LAST_LINE=$(echo "$RAW_DATA" | tail -n 1)
        
        DESIRED=$(echo "$LAST_LINE" | awk '{print $1}')
        ACTUAL=$(echo "$LAST_LINE" | awk '{print $2}')
        READY=$(echo "$LAST_LINE" | awk '{print $3}')

        if [ -n "$ACTUAL" ] && [ "$ACTUAL" -gt 0 ] 2>/dev/null && [ "$ACTUAL" -lt 9223372036854775807 ] 2>/dev/null; then
            if [ "$ACTUAL" -gt "$LAST_PRESENT_TIME" ] && [ "$LAST_PRESENT_TIME" -gt 0 ]; then
                DELTA_NS=$((ACTUAL - LAST_PRESENT_TIME))
                if [ "$DELTA_NS" -gt 500000 ] && [ "$DELTA_NS" -lt 500000000 ]; then
                    DELTA_MS=$((DELTA_NS / 1000000))
                    if [ "$DELTA_MS" -gt 0 ]; then
                        FPS=$((1000 / DELTA_MS))
                        printf "\r\033[K[FPS: \033[1;32m%3d\033[0m | FrameTime: %3d ms | Window: %s]" "$FPS" "$DELTA_MS" "$CUR_SURFACE"
                    fi
                fi
            fi
            LAST_PRESENT_TIME="$ACTUAL"
        fi
    fi

    sleep 0.2
done
