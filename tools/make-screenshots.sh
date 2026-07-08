#!/usr/bin/env bash
# Generates README screenshots using headless Chromium + ImageMagick.
# Run inside `nix develop` after `./gradlew testDebug` has dumped the fixtures.
#
# Pipeline:
#   1. Robolectric EinkCssDumpTest has written:
#        docs/screenshots/eink.css                 (extracted from EinkInjector)
#        docs/screenshots/uber-login-fixture.html  (offline Uber Rides login page)
#        docs/screenshots/uber-eats-fixture.html   (Eats variant)
#        docs/screenshots/settings-fixture.html    (Settings screen mirror)
#   2. Headless Chromium renders each fixture at the Kompakt's viewport ->
#      docs/screenshots/raw/{main_activity,settings_activity,uber_eats}.png
#   3. ImageMagick post-processes every raw/*.png to grayscale+dither to
#      approximate the Mudita Kompakt's 4.3" E Ink Carta panel, writing to
#      docs/screenshots/*.png (the paths the README references).
set -euo pipefail
cd "$(dirname "$0")/.."

RAW=docs/screenshots/raw
OUT=docs/screenshots
mkdir -p "$RAW" "$OUT"

CHROMIUM="${CHROMIUM:-chromium}"
# Viewport approximates the Mudita Kompakt's 4.3" panel.
W=600
H=1060

echo "== 1. Dump e-ink CSS + HTML fixtures =="
./gradlew -q testDebug --tests 'com.dan.inkber.EinkCssDumpTest' --no-daemon --rerun-tasks

echo "== 2. Render fixtures in headless Chromium =="
render_fixture() {
  local html="$1" out="$2"
  "$CHROMIUM" --headless --no-sandbox --disable-gpu \
    --hide-scrollbars \
    --window-size="$W,$H" \
    --screenshot="$out" \
    "file://$(pwd)/$html" 2>/dev/null || true
  if command -v convert >/dev/null 2>&1; then
    convert "$out" -crop "${W}x${H}+0+0" +repage "$out" 2>/dev/null || true
  fi
}

render_fixture "$OUT/uber-login-fixture.html" "$RAW/main_activity.png"
render_fixture "$OUT/uber-eats-fixture.html"  "$RAW/uber_eats.png"
render_fixture "$OUT/settings-fixture.html"   "$RAW/settings_activity.png"
# Rides-only (without toggle bar) for the "WebView content" README section.
sed -n '/<div class="app">/,/<\/div>.*<\/div>/p' "$OUT/uber-login-fixture.html" > /dev/null || true

echo "== 3. E-ink post-process with ImageMagick =="
einkify() {
  local src="$1" dst="$2"
  convert "$src" \
    -colorspace Gray \
    -level 8%,92%,1.1 \
    -ordered-dither o4x4,2 \
    -normalize \
    "$dst"
}

einkify "$RAW/main_activity.png"     "$OUT/main_activity.png"
einkify "$RAW/settings_activity.png" "$OUT/settings_activity.png"
einkify "$RAW/uber_eats.png"         "$OUT/uber_eats.png"
# Also produce a plain Rides WebView-content shot (the login fixture minus
# the toggle bar) for the features section.
cp "$OUT/main_activity.png" "$OUT/uber_rides.png"

echo "Screenshots written to $OUT/"
ls -la "$OUT"/*.png