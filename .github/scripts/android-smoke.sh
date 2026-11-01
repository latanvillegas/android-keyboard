#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p android-evidence
trap 'adb logcat -d -v threadtime > android-evidence/logcat.txt 2>/dev/null || true' EXIT

apk="$(find verified-apks -type f -name '*.apk' ! -name '*androidTest*' | head -n 1)"
test_apk="$(find verified-apks -type f -name '*androidTest*.apk' | head -n 1)"
if [[ -z "$apk" || -z "$test_apk" ]]; then
  echo "Missing main or instrumentation APK" >&2
  exit 1
fi

adb wait-for-device
adb install -r "$apk"
adb install -r "$test_apk"

ime='org.futo.inputmethod.latin.unstable/org.futo.inputmethod.latin.LatinIME'
test_package='org.futo.inputmethod.latin.tests'
activity='org.futo.inputmethod.latin.KeyboardSmokeActivity'
adb shell ime list -s | tee android-evidence/available-imes.txt
adb shell ime enable "$ime"
adb shell ime set "$ime"

check_orientation() {
  local label="$1"
  adb shell am force-stop "$test_package" || true
  adb shell am start -W -n "$test_package/$activity" | tee "android-evidence/start-$label.txt"
  # An IME can be selected without being displayed. Wait for the actual
  # input view (and attempt focus once if the cold-start IME is late).
  local shown=0
  local attempt
  for attempt in $(seq 1 20); do
    adb shell dumpsys input_method > "android-evidence/ime-$label.txt"
    if grep -Fq "mCurMethodId=$ime" "android-evidence/ime-$label.txt" &&
       grep -Eq '^[[:space:]]*mInputShown=true' "android-evidence/ime-$label.txt"; then
      shown=1
      break
    fi

    if [[ "$attempt" == 3 || "$attempt" == 8 ]]; then
      if [[ "$label" == portrait ]]; then
        adb shell input tap 90 1200
      else
        adb shell input tap 90 280
      fi
    fi
    sleep 2
  done

  adb shell dumpsys activity activities > "android-evidence/activity-$label.txt"
  adb exec-out screencap -p > "android-evidence/keyboard-$label.png"
  if [[ "$shown" != 1 ]]; then
    echo "::error::IME was selected but its input view was not shown ($label)" >&2
    return 1
  fi
}

adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
check_orientation portrait

adb shell settings put system user_rotation 1
sleep 3
check_orientation landscape

# Actual production helper tests run on the Android emulator for all modes.
# These do not automate tapping the keyboard's floating/split mode controls.
adb shell am instrument -w \
  -e class org.futo.inputmethod.latin.KeyboardCustomizationSmokeTest \
  "$test_package/androidx.test.runner.AndroidJUnitRunner" \
  | tee android-evidence/instrumentation.txt

if ! grep -Eq '^OK \([1-9][0-9]* tests?\)' android-evidence/instrumentation.txt; then
  echo "Instrumentation suite did not pass" >&2
  exit 1
fi

adb shell settings put system accelerometer_rotation 1
echo "Android IME smoke and four production sizing helper tests completed."
