#!/usr/bin/env bash
# Android TV emulator smoke test for the phase 9 QA build.
# Runs ONLY on the disposable GitHub Actions emulator, never a user's TCL.
set -euo pipefail

if [ "$#" -ne 1 ]; then echo "Usage: $0 <apk>"; exit 2; fi
APK="$1"
PKG="io.freemytv.homep9test"
HOME_COMPONENT="$PKG/io.freemytv.HomeActivity"
OUT="emulator-results"
mkdir -p "$OUT"
# Retain crash evidence even when an intermediate assertion exits nonzero.
trap 'adb logcat -d -v threadtime -s AndroidRuntime:E FreeMyTV:E ActivityTaskManager:W > "$OUT/logcat-errors.txt" 2>/dev/null || true; adb shell dumpsys activity activities > "$OUT/final-activities.txt" 2>/dev/null || true' EXIT
adb wait-for-device
adb shell wm size | tee "$OUT/display.txt"
if ! grep -Eq 'Physical size: (1920x1080|1280x720|3840x2160)' "$OUT/display.txt"; then
  echo "FAIL: emulator is not running a supported landscape TV profile" | tee -a "$OUT/checks.txt"
  exit 1
fi
adb install -r "$APK" | tee "$OUT/install.txt"

capture() {
  local name="$1"
  adb shell dumpsys activity activities > "$OUT/$name-activities.txt" || true
  adb shell dumpsys window windows > "$OUT/$name-window.txt" || true
  adb exec-out screencap -p > "$OUT/$name.png" || true
  adb shell uiautomator dump /sdcard/window.xml > "$OUT/$name-uiauto-command.txt" 2>&1 || true
  adb shell cat /sdcard/window.xml > "$OUT/$name-ui.xml" 2>/dev/null || true
}
top_activity() {
  adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity|ResumedActivity" | tail -n 4
}
assert_activity() {
  local expected="$1"
  local top
  top="$(top_activity)"
  echo "$top" | tee -a "$OUT/checks.txt"
  if ! grep -q "$expected" <<< "$top"; then
    echo "FAIL: expected $expected to be resumed" | tee -a "$OUT/checks.txt"
    exit 1
  fi
}
open_home() {
  adb shell am start -W -n "$HOME_COMPONENT" >> "$OUT/launch-home.txt"
  sleep 3
  assert_activity "HomeActivity"
}
key() {
  adb shell input keyevent "$1"
  sleep 1
}

open_home
capture home
assert_activity "HomeActivity"
if ! grep -q "Agora na TV" "$OUT/home-ui.xml"; then
  echo "FAIL: native top navigation is missing; dashboard may have fallen back" | tee -a "$OUT/checks.txt"
  exit 1
fi
if ! grep -q "Filmes" "$OUT/home-ui.xml"; then
  echo "FAIL: Filmes tab missing in Home" | tee -a "$OUT/checks.txt"
  exit 1
fi
echo "PASS: native Home visible and resumed" | tee -a "$OUT/checks.txt"

# Native D-pad navigation starting from Início. Never opens external UniTVnet.
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_CENTER
sleep 3
capture guide
assert_activity "EpgActivity"
echo "PASS: Agora na TV opens from native HOME with D-pad" | tee -a "$OUT/checks.txt"

open_home
capture returned-home
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_RIGHT
capture movies-before-enter
key KEYCODE_DPAD_CENTER
sleep 3
capture movies
assert_activity "MoviesActivity"
echo "PASS: Filmes opens from native HOME with D-pad" | tee -a "$OUT/checks.txt"

open_home
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_CENTER
sleep 3
capture series
assert_activity "SeriesActivity"
echo "PASS: Séries opens from native HOME with D-pad" | tee -a "$OUT/checks.txt"

open_home
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_RIGHT
key KEYCODE_DPAD_CENTER
sleep 3
capture apps
assert_activity "HomeActivity"
if ! grep -q "Seus aplicativos" "$OUT/apps-ui.xml"; then
  echo "FAIL: Apps tab did not display the app grid" | tee -a "$OUT/checks.txt"
  exit 1
fi
echo "PASS: Apps grid opens with D-pad" | tee -a "$OUT/checks.txt"

adb logcat -d -v threadtime -s AndroidRuntime:E FreeMyTV:E > "$OUT/logcat-errors.txt" || true
if grep -qE "Process: $PKG(:home)?([[:space:]]|$)" "$OUT/logcat-errors.txt"; then
  echo "FAIL: AndroidRuntime crash for the tested app" | tee -a "$OUT/checks.txt"
  exit 1
fi
echo "PASS: basic emulator lifecycle, top navigation and no detected app crash" | tee -a "$OUT/checks.txt"
