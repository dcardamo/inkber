#!/usr/bin/env bash
# Boots a headless Android emulator, runs the instrumented SPA test, and
# captures reference screenshots. Run inside `nix develop`. Requires KVM.
#
# This is intentionally a local smoke-test helper, not part of CI, because
# emulator boot is too slow and flaky for GitHub Actions.
set -euo pipefail

ADB="${ADB:-adb}"
EMULATOR="${EMULATOR:-emulator}"
AVD_NAME="inkber-test"
DISPLAY_NUM="99"

# ---- 1. Sanity check environment ----
command -v "$ADB" >/dev/null 2>&1 || { echo "adb not found"; exit 1; }
command -v "$EMULATOR" >/dev/null 2>&1 || { echo "emulator not found"; exit 1; }

# ---- 2. Create AVD if needed ----
if ! "$ADB" devices 2>/dev/null | grep -q emulator; then
  echo "Creating AVD..."
  echo "no" | avdmanager create avd \
    -n "$AVD_NAME" \
    -k "system-images;android-34;google_apis;x86_64" \
    -d "pixel_6" \
    --force 2>/dev/null || true
fi

# ---- 3. Start Xvfb ----
export DISPLAY=":$DISPLAY_NUM"
Xvfb :$DISPLAY_NUM -screen 0 1080x1920x24 \
  -listen tcp \
  -nolisten unix &
XVFB_PID=$!
sleep 2

# ---- 4. Boot emulator headless ----
echo "Booting emulator..."
"$EMULATOR" -avd "$AVD_NAME" \
  -no-window \
  -no-audio \
  -no-snapshot \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  -port 5554 &
EMU_PID=$!

# ---- 5. Wait for device and boot ----
echo "Waiting for device..."
"$ADB" wait-for-device

echo "Waiting for boot..."
BOOT_DONE=0
for i in $(seq 1 180); do
  BOOTPROP=$("$ADB" shell getprop sys.boot_completed 2>/dev/null || echo "")
  if [ "$BOOTPROP" = "1" ]; then
    BOOT_DONE=1
    echo "Boot complete after ${i}s"
    break
  fi
  sleep 1
done

if [ "$BOOT_DONE" != "1" ]; then
  echo "ERROR: Emulator did not boot within 180s"
  kill $EMU_PID $XVFB_PID 2>/dev/null || true
  exit 1
fi

# ---- 6. Generate fixtures and run instrumented tests ----
echo "Generating fixtures..."
./gradlew testDebug --tests "com.dan.inkber.EinkCssDumpTest" --no-daemon -q

echo "Running instrumented Android tests..."
./gradlew connectedDebugAndroidTest --no-daemon

# ---- 7. Capture reference screenshots ----
echo "Capturing reference screenshots..."
mkdir -p docs/screenshots/emulator

"$ADB" shell am start \
  -n com.dan.inkber/.MainActivity \
  -a android.intent.action.MAIN \
  --ez inkber.SKIP_LOCATION_PROMPT true \
  --es inkber.TEST_URL_RIDES "file:///android_asset/fixtures/uber-spa-fixture.html" \
  --es inkber.TEST_URL_EATS "file:///android_asset/fixtures/uber-spa-fixture.html"
sleep 6
"$ADB" exec-out screencap -p > docs/screenshots/emulator/01_spa_loaded.png

"$ADB" shell input tap 800 2350
sleep 4
"$ADB" exec-out screencap -p > docs/screenshots/emulator/02_spa_eats.png

"$ADB" shell pm clear com.dan.inkber >/dev/null 2>&1 || true
"$ADB" shell am start -n com.dan.inkber/.MainActivity
sleep 3
"$ADB" exec-out screencap -p > docs/screenshots/emulator/03_location_prompt.png

echo "Screenshots saved to docs/screenshots/emulator/"
ls -la docs/screenshots/emulator/

# ---- 8. Cleanup ----
echo "Shutting down emulator..."
"$ADB" emu kill 2>/dev/null || true
kill $EMU_PID $XVFB_PID 2>/dev/null || true
echo "Done."
