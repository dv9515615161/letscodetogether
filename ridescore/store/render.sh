#!/usr/bin/env bash
# Regenerates the Play Store graphics from the HTML in this directory.
#
# Headless Chrome sizes a screenshot to the window, not the page, and reserves
# part of the window for browser chrome - so we render into an over-tall window
# and crop back to the exact size Play requires.
set -euo pipefail

CHROME="${CHROME:-/opt/pw-browsers/chromium-1194/chrome-linux/chrome}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SLACK=200  # extra window height to keep the page clear of browser chrome

render() {
  local html="$1" out="$2" w="$3" h="$4"
  "$CHROME" --headless=new --disable-gpu --no-sandbox --hide-scrollbars \
    --force-device-scale-factor=1 --window-size="$w,$((h + SLACK))" \
    --screenshot="$HERE/.raw.png" "file://$HERE/$html" 2>/dev/null || true
  python3 "$HERE/crop_png.py" "$HERE/.raw.png" "$HERE/$out" "$w" "$h"
  rm -f "$HERE/.raw.png"
}

render icon.html    icon-512.png                  512  512
render feature.html feature-graphic-1024x500.png 1024  500
