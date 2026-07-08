#!/usr/bin/env bash
# Boots a headless Android emulator, installs the debug APK, and captures
# screenshots of the app rendering local HTML fixtures. Run inside `nix develop`.
#
# Requires KVM. Uses Xvfb for a virtual display since saturn is headless.
set -euo pipefail

ADB="${ADB:-adb}"
EMULATOR="${EMULATOR:-emulator}"
AVD_NAME="inkber-test"
DISPLAY_NUM="99"

# ---- 1. Create AVD if it doesn't exist ----
if ! "$ADB" devices 2>/dev/null | grep -q emulator; then
  echo "Creating AVD..."
  echo "no" | avdmanager create avd \
    -n "$AVD_NAME" \
    -k "system-images;android-34;google_apis;x86_64" \
    -d "pixel_6" \
    --force 2>/dev/null || true
fi

# ---- 2. Start Xvfb ----
export DISPLAY=":$DISPLAY_NUM"
Xvfb :$DISPLAY_NUM -screen 0 1080x1920x24 \
  -listen tcp \
  -nolisten unix &
XVFB_PID=$!
sleep 2

# ---- 3. Generate fixtures and push to emulator ----
FIXTURE_SRC="docs/screenshots"
echo "Generating fixtures..."
./gradlew testDebug --tests "com.dan.inkber.EinkCssDumpTest" --no-daemon -q

echo "Pushing fixtures to emulator..."
"$ADB" shell mkdir -p /sdcard/inkber
"$ADB" push "$FIXTURE_SRC/uber-rides-dark-fixture.html" /sdcard/inkber/rides.html >/dev/null 2>&1
"$ADB" push "$FIXTURE_SRC/uber-eats-dark-fixture.html" /sdcard/inkber/eats.html >/dev/null 2>&1
"$ADB" push "$FIXTURE_SRC/eink.css" /sdcard/inkber/eink.css >/dev/null 2>&1

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

# ---- 5. Wait for boot ----
echo "Waiting for boot..."
BOOT_DONE=0
for i in $(seq 1 120); do
  BOOTPROP=$("$ADB" shell getprop sys.boot_completed 2>/dev/null || echo "")
  if [ "$BOOTPROP" = "1" ]; then
    BOOT_DONE=1
    echo "Boot complete after ${i}s"
    break
  fi
  sleep 1
done

if [ "$BOOT_DONE" != "1" ]; then
  echo "ERROR: Emulator did not boot within 120s"
  kill $EMU_PID $XVFB_PID 2>/dev/null || true
  exit 1
fi

# ---- 6. Install APK ----
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "Building debug APK..."
  ./gradlew assembleDebug --no-daemon -q
fi

echo "Installing APK..."
"$ADB" install -r "$APK" 2>&1

# ---- 7. Launch app with test URLs and screenshot ----
echo "Launching Inkber with local fixtures..."
"$ADB" shell am start \
  -n com.dan.inkber/.MainActivity \
  -a android.intent.action.MAIN \
  --ez inkber.SKIP_LOCATION_PROMPT true \
  --es inkber.TEST_URL_RIDES "file:///sdcard/inkber/rides.html" \
  --es inkber.TEST_URL_EATS "file:///sdcard/inkber/eats.html"
sleep 6

mkdir -p docs/screenshots/emulator

echo "Capturing screenshots..."
"$ADB" exec-out screencap -p > docs/screenshots/emulator/01_launch.png

# Tap the Eats tab (rightmost of the three bottom buttons).
"$ADB" shell input tap 800 2350
sleep 6
"$ADB" exec-out screencap -p > docs/screenshots/emulator/04_eats_tab.png

# Switch back to Rides tab
"$ADB" shell input tap 200 2350
sleep 4
"$ADB" exec-out screencap -p > docs/screenshots/emulator/05_rides_tab.png

# Open settings
"$ADB" shell input tap 950 2350
sleep 2
"$ADB" exec-out screencap -p > docs/screenshots/emulator/06_settings.png

# Optional: also run the interactive location-prompt flow on a second launch
# (without the skip extra) and capture the prompt for golden comparison.
"$ADB" shell pm clear com.dan.inkber >/dev/null 2>&1 || true
"$ADB" shell am start -n com.dan.inkber/.MainActivity
sleep 3
"$ADB" exec-out screencap -p > docs/screenshots/emulator/02_location_prompt.png

echo "Screenshots saved to docs/screenshots/emulator/"
ls -la docs/screenshots/emulator/

# ---- 8. Cleanup ----
echo "Shutting down emulator..."
"$ADB" emu kill 2>/dev/null || true
kill $EMU_PID $XVFB_PID 2>/dev/null || true
echo "Done."