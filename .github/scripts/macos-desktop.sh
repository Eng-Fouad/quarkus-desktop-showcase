#!/bin/bash
# The desktop of a hosted macOS runner for the showcase (tools/Cycle.java), before and after a cycle : the session, the
# display, the front application, the privacy permissions (Screen Recording and Accessibility for Robot), a screenshot.
# With --prepare, first : the largest display mode at scale 1 up to 3840x2160 (macos-display.swift). CI only (it changes
# the display mode). Best effort : always exits 0 ; the showcase reports what it got (report.json : screen, macos.tcc.*).
# usage: macos-desktop.sh [--prepare] [screenshot.png]      (bash 3.2)
here=$(cd "$(dirname "$0")" && pwd)
if [ "$1" = --prepare ]; then
    shift
    sw_vers
    csrutil status
    xcrun swift "$here/macos-display.swift" 2>&1
fi
echo "session: $(launchctl managername)"          # Aqua : a graphical session (AWT is headless otherwise)
system_profiler SPDisplaysDataType 2>/dev/null | sed -n '/Displays:/,$p'
echo "front application: $(lsappinfo info -only name "$(lsappinfo front)" 2>&1)"
lsappinfo visibleProcessList 2>&1
# the grants of the image (actions/runner-images configure-tccdb-macos.sh) : the system database (readable with Full Disk
# Access only) and the database of the user (/bin/bash)
query="select service, client, auth_value from access where service in ('kTCCServiceScreenCapture',
    'kTCCServiceAccessibility', 'kTCCServicePostEvent') order by client"
echo "TCC (system):"; sudo sqlite3 "/Library/Application Support/com.apple.TCC/TCC.db" "$query" 2>&1
echo "TCC (user):"; sqlite3 "$HOME/Library/Application Support/com.apple.TCC/TCC.db" "$query" 2>&1
if [ -n "$1" ]; then screencapture -x "$1" && echo "screenshot: $1"; fi
exit 0
