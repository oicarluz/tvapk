#!/usr/bin/env bash
# Android emulator smoke for Free My TV phase 9 test package.
# Never changes the device's HOME role or disables a package.
set -euo pipefail

PKG="io.freemytv.homep9test"
APK="$1"
REPORT="$2"
mkdir -p "$REPORT"

adb wait-for-device
BOOT="$(adb shell getprop sys.boot_completed | tr -d '\r')"
if [[ "$BOOT" != "1" ]]; then
  echo "Emulator not completely booted." >&2; exit 1
fi

adb install -r "$APK" | tee "$REPORT/install.txt"
adb shell pm path "$PKG" | tee "$REPORT/package.txt"
adb logcat -c
adb shell am start -W -n "$PKG/io.freemytv.HomeActivity" | tee "$REPORT/home-launch.txt"
sleep 5

PIDS="$(adb shell pidof "$PKG:home" | tr -d ' \r\n' || true)"
if [[ -z "$PIDS" ]]; then
  echo "ERROR: Home process not running." >&2
  adb logcat -d -v time -s AndroidRuntime:E FreeMyTV:E > "$REPORT/crashes.txt"
  exit 1
fi
echo "$PIDS" > "$REPORT/home-pid.txt"

adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_LEFT
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_UP
sleep 1
adb shell dumpsys window windows > "$REPORT/windows.txt"
adb shell dumpsys activity activities > "$REPORT/activities.txt"

# The GUIDE key is a public Home shortcut to non-exported EpgActivity.
adb shell input keyevent KEYCODE_GUIDE
sleep 5
adb shell dumpsys activity activities > "$REPORT/guide-activity.txt"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell am start -W -n "$PKG/io.freemytv.HomeActivity" > "$REPORT/home-resume.txt"
sleep 2

adb logcat -d -v time -s AndroidRuntime:E FreeMyTV:E > "$REPORT/crashes.txt"
if grep -A 32 'FATAL EXCEPTION' "$REPORT/crashes.txt" |
  grep -E "Process: io.freemytv.homep9test(:home)?[, ]" >/dev/null; then
  echo "ERROR: Free My TV fatal exception detected." >&2
  cat "$REPORT/crashes.txt"
  exit 1
fi
PIDS_AFTER="$(adb shell pidof "$PKG:home" | tr -d ' \r\n' || true)"
if [[ -z "$PIDS_AFTER" ]]; then
  echo "ERROR: Home process gone after navigation." >&2
  exit 1
fi

echo "PASS emulator: app installed, Home alive after remote navigation and GUIDE; no app crash."
