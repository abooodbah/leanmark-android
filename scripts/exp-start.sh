#!/usr/bin/env bash
# Experiment only: cold start to the document drawn, simple.md, several builds in turns.
set -uo pipefail
PKG=io.github.abooodbah.leanmark
labels=(); declare -A apk=() drawn=() dom=() loaded=() step=()
for a in "$@"; do labels+=("${a%%=*}"); apk[${a%%=*}]="${a#*=}"; done
for round in 1 2 3 4 5; do
  for l in "${labels[@]}"; do
    adb install -r -d "${apk[$l]}" >/dev/null
    for i in 0 1 2 3; do
      adb shell am force-stop $PKG; adb logcat -c
      adb shell am start-activity -W -n $PKG/.MainActivity -a android.intent.action.VIEW -d content://$PKG.fixtures/simple.md -t text/markdown --grant-read-uri-permission >/dev/null
      sleep 4
      [ $i = 0 ] && continue   # warm-up after install
      log=$(adb logcat -d -v raw -s LeanMarkTiming:I ActivityTaskManager:I | tr -d '\r')
      d=$(grep -o 'Fully drawn [^:]*: +[0-9a-z]*' <<<"$log" | head -1 | sed -E 's/.*\+//; s/^([0-9]+)s([0-9]+)ms$/\1*1000+\2/; s/ms$//' | bc 2>/dev/null)
      drawn[$l]+="${d:-NA} "
      dom[$l]+="$(sed -n 's/^page page-dom \([0-9]*\) ms$/\1/p' <<<"$log" | head -1) "
      loaded[$l]+="$(sed -n 's/^phase page loaded \([0-9]*\) ms$/\1/p' <<<"$log" | head -1) "
      step[$l]+="$(sed -n 's/^phase drawn \([0-9]*\) ms$/\1/p' <<<"$log" | head -1) "
    done
  done
done
med() { tr ' ' '\n' | grep -E '^[0-9]+$' | sort -n | awk '{v[NR]=$1} END {print v[int((NR+1)/2)] " (n=" NR ")"}'; }
echo "| build | Fully drawn | page-dom | page loaded | drawn step |"
for l in "${labels[@]}"; do echo "| $l | $(med <<<"${drawn[$l]}") | $(med <<<"${dom[$l]}") | $(med <<<"${loaded[$l]}") | $(med <<<"${step[$l]}") |"; done
for l in "${labels[@]}"; do echo "raw $l drawn: ${drawn[$l]}"; done
