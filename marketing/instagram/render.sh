#!/usr/bin/env bash
# Renders every slide HTML in this folder to PNG with headless Chrome (or Edge).
#   square/<slide>.png    1080×1080
#   portrait/<slide>.png  1080×1350   (same HTML, opened with #portrait)
# Usage: bash marketing/instagram/render.sh
set -euo pipefail
cd "$(dirname "$0")"

BROWSER="${BROWSER:-}"
for b in "/c/Program Files/Google/Chrome/Application/chrome.exe" \
         "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
         google-chrome chromium chromium-browser; do
  [ -n "$BROWSER" ] && break
  if [ -x "$b" ] || command -v "$b" >/dev/null 2>&1; then BROWSER="$b"; fi
done
[ -n "$BROWSER" ] || { echo "No Chrome/Edge found; set BROWSER=/path/to/chrome"; exit 1; }

here="$(pwd -W 2>/dev/null || pwd)"
mkdir -p square portrait

render() { # html out width height hash
  "$BROWSER" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --allow-file-access-from-files --virtual-time-budget=4000 \
    --window-size="$3,$4" --screenshot="$here/$2" "file:///$here/$1$5" >/dev/null 2>&1
  echo "  $2"
}

for f in [0-9][0-9]-*.html; do
  name="${f%.html}"
  render "$f" "square/$name.png" 1080 1080 ""
  render "$f" "portrait/$name.png" 1080 1350 "#portrait"
done
