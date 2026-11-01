#!/usr/bin/env bash
# Regenerates the SVGs and exports PNGs (transparent backgrounds) with headless Chrome/Edge.
#   png/<name>.png      at 2x the SVG's size (icons: 1024 px)
#   board.png           overview of both concepts
# Usage: bash marketing/brand/render.sh
set -euo pipefail
cd "$(dirname "$0")"
python make_logo.py >/dev/null

BROWSER="${BROWSER:-}"
for b in "/c/Program Files/Google/Chrome/Application/chrome.exe" \
         "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" \
         google-chrome chromium chromium-browser; do
  [ -n "$BROWSER" ] && break
  if [ -x "$b" ] || command -v "$b" >/dev/null 2>&1; then BROWSER="$b"; fi
done
[ -n "$BROWSER" ] || { echo "No Chrome/Edge found; set BROWSER=/path/to/chrome"; exit 1; }

here="$(pwd -W 2>/dev/null || pwd)"
mkdir -p png
shoot() { # url out w h scale
  "$BROWSER" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor="$5" \
    --default-background-color=00000000 --allow-file-access-from-files --virtual-time-budget=3000 \
    --window-size="$3,$4" --screenshot="$here/$2" "file:///$here/$1" >/dev/null 2>&1
  echo "  $2"
}

for f in svg/*.svg; do
  name="$(basename "$f" .svg)"
  read -r w h < <(sed -E 's/.*width="([0-9.]+)" height="([0-9.]+)".*/\1 \2/;q' "$f")
  scale=2; [[ "$name" == *-icon ]] && scale=1
  shoot "$f" "png/$name.png" "${w%.*}" "${h%.*}" "$scale"
done
shoot board.html board.png 1600 940 1
