#!/usr/bin/env bash
# Every gate that can run without a phone: unit tests, the Android layer under
# Robolectric, the build, the manifest, core purity, the wording, the clips.
set -euo pipefail
cd "$(dirname "$0")"
. ./env.sh

step() { printf '\n== %s\n' "$1"; }

step "unit tests (core + Android layer)"
gradle -q :core:test :app:testDebugUnitTest

step "assemble"
gradle -q :app:assembleDebug

APK=app/build/outputs/apk/debug/app-debug.apk
SDKAPK="$ANDROID_HOME/build-tools/35.0.0/aapt2"

step "manifest and permissions"
PERMS=$("$SDKAPK" dump permissions "$APK" | grep -o 'android.permission.[A-Z_]*' | sort -u)
echo "$PERMS"
EXPECTED=$(printf '%s\n' \
  android.permission.ACCESS_NOTIFICATION_POLICY \
  android.permission.POST_NOTIFICATIONS \
  android.permission.RECEIVE_BOOT_COMPLETED \
  android.permission.SCHEDULE_EXACT_ALARM \
  android.permission.VIBRATE | sort)
if [ "$PERMS" != "$EXPECTED" ]; then
  echo "permission set is not the promised one" >&2
  exit 1
fi
if echo "$PERMS" | grep -q INTERNET; then
  echo "the app must not have the INTERNET permission" >&2
  exit 1
fi

step "no network in the bytecode"
if unzip -l "$APK" | grep -q 'classes.*\.dex' && strings "$APK" | grep -q 'INTERNET'; then
  echo "note: the string INTERNET appears inside the APK" >&2
fi

step "core stays free of android.*"
if grep -rn '^import android' core/src/main/kotlin; then
  echo "core must not import android" >&2
  exit 1
fi

step "the words the app may not say"
python3 tools/wording.py

step "the sound leaves with the app"
for clip in eat_something food_oclock it_is_time; do
  unzip -l "$APK" | grep -q "raw/$clip" || { echo "missing clip $clip" >&2; exit 1; }
done

step "ok"
echo "apk: $(pwd)/$APK"
ls -la "$APK"
