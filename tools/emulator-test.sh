#!/usr/bin/env bash
# Boots a headless Android emulator, installs the debug APK, and captures
# screenshots. Run inside `nix develop`.
#
# Requires KVM. Uses Xvfb for a virtual display since saturn is headless.
set -euo pipefail

ADB="${ADB:-adb}"
EMULATOR="${EMULATOR:-emulator}"
AVD_NAME="inkber-test"
DISPLAY_NUM="99"

# ---- 1. Create AVD if it doesn't exist ----
if ! "$ADB" devices | grep -q emulator; then
  echo "Creating AVD..."
  echo "no" | avdmanager create avd \
    -n "$AVD_NAME" \
    -k "system-images;android-34;google_apis;x86_64" \
    -d "pixel_6" \
    --force 2>/dev/null || true
fi

# ---- 2. Start Xvfb ----
export DISPLAY=":$DISPLAY_NUM"
Xvfb :$DISPLAY_NUM -screen 0 1080x1920x24 &
XVFB_PID=$!
sleep 2

# ---- 3. Boot emulator headless ----
echo "Booting emulator..."
"$EMULATOR" -avd "$AVD_NAME" \
  -no-window \
  -no-audio \
  -no-snapshot \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  -port 5554 &
EMU_PID=$!

# ---- 4. Wait for boot ----
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

# ---- 5. Install APK ----
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "Building debug APK..."
  ./gradlew assembleDebug --no-daemon -q
fi

echo "Installing APK..."
"$ADB" install -r "$APK" 2>&1

# ---- 6. Launch app and screenshot ----
echo "Launching Inkber..."
"$ADB" shell am start -n com.dan.inkber/.MainActivity
sleep 5

mkdir -p docs/screenshots/emulator

echo "Capturing screenshots..."
"$ADB" exec-out screencap -p > docs/screenshots/emulator/01_launch.png

# Wait for location dialog to appear
sleep 3
"$ADB" exec-out screencap -p > docs/screenshots/emulator/02_location_dialog.png

# Tap "Allow" (top button) — approximate coordinates for a 1080x2400 screen
# The dialog button is roughly in the bottom third
"$ADB" shell input tap 540 1450
sleep 2
"$ADB" exec-out screencap -p > docs/screenshots/emulator/03_after_allow.png

# Switch to Eats tab
"$ADB" shell input tap 800 2350
sleep 5
"$ADB" exec-out screencap -p > docs/screenshots/emulator/04_eats_tab.png

# Switch back to Rides tab
"$ADB" shell input tap 200 2350
sleep 3
"$ADB" exec-out screencap -p > docs/screenshots/emulator/05_rides_tab.png

# Open settings
"$ADB" shell input tap 950 2350
sleep 2
"$ADB" exec-out screencap -p > docs/screenshots/emulator/06_settings.png

echo "Screenshots saved to docs/screenshots/emulator/"
ls -la docs/screenshots/emulator/

# ---- 7. Cleanup ----
echo "Shutting down emulator..."
"$ADB" emu kill 2>/dev/null || true
kill $EMU_PID $XVFB_PID 2>/dev/null || true
echo "Done."