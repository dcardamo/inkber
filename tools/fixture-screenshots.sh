#!/usr/bin/env bash
# Renders every docs/screenshots/*-fixture.html with headless Chromium and
# asserts the screenshot is e-ink friendly (mostly light, not too much dark area).
#
# Run inside `nix develop` after `EinkCssDumpTest` has generated the fixtures.
set -euo pipefail

CHROME="${CHROME:-chromium}"
FIXTURE_DIR="${FIXTURE_DIR:-docs/screenshots}"
OUT_DIR="${OUT_DIR:-docs/screenshots/raw}"
MIN_BRIGHTNESS="${MIN_BRIGHTNESS:-0.65}"
MAX_DARK_RATIO="${MAX_DARK_RATIO:-0.25}"
DARK_THRESHOLD_PERCENT="${DARK_THRESHOLD_PERCENT:-35}"

# ImageMagick v7 uses `magick`; older versions use `convert`.
if command -v magick >/dev/null 2>&1; then
  MAGICK="magick"
else
  MAGICK="convert"
fi

mkdir -p "$OUT_DIR"

failures=0

for fixture in "$FIXTURE_DIR"/*-fixture.html; do
  [ -e "$fixture" ] || continue
  name=$(basename "$fixture" .html)
  out="$OUT_DIR/${name}.png"
  echo "Rendering $name..."
  "$CHROME" \
    --headless \
    --disable-gpu \
    --no-sandbox \
    --hide-scrollbars \
    --window-size=390,844 \
    --screenshot="$out" \
    --virtual-time-budget=1000 \
    --run-all-compositor-stages-before-draw \
    "file://$(realpath "$fixture")" >/dev/null 2>&1

  # ImageMagick identify: mean is returned as a fraction 0..1 (QuantumRange scaled).
  read mean _ < <("$MAGICK" "$out" -colorspace Gray -format "%[fx:mean]\n" info:)
  # True dark ratio = fraction of pixels below the brightness threshold.
  read light_ratio _ < <("$MAGICK" "$out" -colorspace Gray \
    -threshold "${DARK_THRESHOLD_PERCENT}%" \
    -format "%[fx:mean]\n" info:)
  dark_ratio=$(awk "BEGIN { print 1 - $light_ratio }")

  echo "  $name: mean brightness=$mean  dark_ratio=$dark_ratio"

  if awk "BEGIN { exit ($mean >= $MIN_BRIGHTNESS ? 0 : 1) }"; then :; else
    echo "  FAIL: brightness below $MIN_BRIGHTNESS"
    failures=$((failures + 1))
  fi

  if awk "BEGIN { exit ($dark_ratio <= $MAX_DARK_RATIO ? 0 : 1) }"; then :; else
    echo "  FAIL: dark ratio above $MAX_DARK_RATIO"
    failures=$((failures + 1))
  fi
done

if [ "$failures" -gt 0 ]; then
  echo "ERROR: $failures fixture(s) failed the e-ink brightness check"
  exit 1
fi

echo "All fixtures rendered and verified light (e-ink compatible)."
echo "Screenshots saved to $OUT_DIR/"
