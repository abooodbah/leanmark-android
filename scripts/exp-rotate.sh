#!/usr/bin/env bash
# Experiment only: rotation timing on large.md for several builds.
set -uo pipefail
PKG=io.github.abooodbah.leanmark
for argument in "$@"; do
  label="${argument%%=*}"; apk="${argument#*=}"
  adb install -r -d "$apk" >/dev/null
  values=""
  for round in 1 2; do
    adb shell am force-stop "$PKG"; adb logcat -c
    adb shell am start-activity -W -n "$PKG/.MainActivity" -a android.intent.action.VIEW -d "content://$PKG.fixtures/large.md" -t text/markdown --grant-read-uri-permission >/dev/null
    sleep 25
    adb shell settings put system accelerometer_rotation 0
    for rotation in 1 0 1 0; do
      adb shell settings put system user_rotation "$rotation"; sleep 6
    done
    values+=" $(adb logcat -d -v raw -s LeanMarkTiming:I | sed -n 's/^page resize \([0-9]*\) ms$/\1/p' | tr '\n' ' ')"
  done
  echo "| $label | $values |"
done
