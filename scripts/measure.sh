#!/usr/bin/env bash
# Measures release builds on a running emulator and prints a Markdown table.
#
#   scripts/measure.sh empty=path/to/empty.apk base=path/to/base.apk branch=path/to/branch.apk
#
# The instrumented test APK must be installed first: its fixture provider
# serves the files as content:// URIs. "empty" is the baseline module, an app
# with one empty activity; its start time is subtracted, because on an
# emulator without a GPU even an empty app takes over a second. Builds take
# turns, so drift in the emulator affects both alike. Every number comes from
# am start -W, the "Fully drawn" line the app logs through reportFullyDrawn(),
# the app's own LeanMarkTiming lines, dumpsys meminfo and dumpsys gfxinfo.
# Page work (theme, zoom, find, rotation) is driven with keys as from a
# hardware keyboard, and timed by the page from the key to the result on
# screen.
set -euo pipefail

PKG=io.github.abooodbah.leanmark
ACTIVITY=$PKG/.MainActivity
EMPTY_PKG=$PKG.baseline
FIXTURES=content://$PKG.fixtures
SMALL=simple.md
SMALL_NAME="simple.md"
SMALL_TYPE=text/markdown
DRAWN_WHAT=document
LARGE=large.md
LARGE_NAME="large.md"
LARGE_TYPE=text/markdown
WEBVIEW=yes
MEASURE_INDEXING=no
TIMING_TAG=LeanMarkTiming

ROUNDS=${ROUNDS:-3}
STARTS_PER_ROUND=${STARTS_PER_ROUND:-3}
STARTS=$((ROUNDS * STARTS_PER_ROUND))

declare -a LABELS=()
declare -A APKS=()
EMPTY_APK=""
for argument in "$@"; do
  label="${argument%%=*}"
  if [ "$label" = empty ]; then
    EMPTY_APK="${argument#*=}"
  else
    LABELS+=("$label")
    APKS["$label"]="${argument#*=}"
  fi
done

log() { echo "[measure] $*" >&2; }

install() {
  adb install -r -d "${APKS[$1]}" >/dev/null
}

open_document() {
  adb shell am force-stop "$PKG"
  adb logcat -c
  adb shell am start-activity -W -n "$ACTIVITY" -a android.intent.action.VIEW \
    -d "$FIXTURES/$1" -t "$2" --grant-read-uri-permission | tr -d '\r'
}

# Milliseconds from "+1s234ms" or "+987ms".
to_ms() {
  sed -E 's/^\+//; s/^([0-9]+)s([0-9]+)ms$/\1 \2/; s/^([0-9]+)ms$/0 \1/' <<<"$1" |
    awk '{ print $1 * 1000 + $2 }'
}

# Waits for the app's reportFullyDrawn() and prints its time, or NA.
fully_drawn() {
  local limit=$1 line
  for _ in $(seq 1 $((limit * 10))); do
    line=$(adb logcat -d | grep -o "Fully drawn $PKG/\.MainActivity: +[0-9a-z]*" | head -n 1 || true)
    if [ -n "$line" ]; then
      to_ms "${line##*: }"
      return
    fi
    sleep 0.1
  done
  echo NA
}

median() {
  local values
  values=$(grep -E '^[0-9]+$' | sort -n || true)
  if [ -z "$values" ]; then echo NA; return; fi
  awk '{ v[NR] = $1 } END { if (NR % 2) print v[(NR + 1) / 2]; else print int((v[NR / 2] + v[NR / 2 + 1]) / 2) }' <<<"$values"
}

minus() {
  if [[ "$1" =~ ^[0-9]+$ && "$2" =~ ^[0-9]+$ ]]; then echo $(($1 - $2)); else echo NA; fi
}

plus() {
  if [[ "$1" =~ ^[0-9]+$ && "$2" =~ ^[0-9]+$ ]]; then echo $(($1 + $2)); else echo NA; fi
}

# The empty app first, on the same emulator: its start time is subtracted.
EMPTY_FIRST=NA
if [ -n "$EMPTY_APK" ]; then
  adb install -r -d "$EMPTY_APK" >/dev/null
  values=""
  for _ in $(seq 0 "$STARTS"); do
    adb shell am force-stop "$EMPTY_PKG"
    total=$(adb shell am start-activity -W -n "$EMPTY_PKG/.EmptyActivity" | tr -d '\r' | sed -n 's/^TotalTime: *//p')
    values+="${total:-NA}"$'\n'
  done
  # The first start after install is a warm-up and is left out.
  EMPTY_FIRST=$(tail -n +2 <<<"$values" | median)
  log "empty app: first frame $EMPTY_FIRST ms (median of $STARTS)"
fi

# The app's "phase NAME N ms" lines (ms from the activity's creation) and the
# page's "page page-dom N ms" lines (ms from the page's navigation start), as
# "name value" pairs with spaces in names turned into underscores.
step_lines() {
  adb logcat -d -v raw -s "${TIMING_TAG:-none}:I" |
    sed -n -e 's/^phase \(.*\) \([0-9][0-9]*\) ms$/\1|\2/p' -e 's/^page \(page-[a-z]*\|render\) \([0-9][0-9]*\) ms$/\1|\2/p' |
    tr ' ' '_' | tr '|' ' '
}

declare -A FIRST_FRAME=() DRAWN=() PHASES=() PHASE_NAMES=()
for round in $(seq 1 "$ROUNDS"); do
  for label in "${LABELS[@]}"; do
    install "$label"
    open_document "$SMALL" "$SMALL_TYPE" >/dev/null # Warm-up after install.
    fully_drawn 10 >/dev/null
    for _ in $(seq 1 "$STARTS_PER_ROUND"); do
      total=$(open_document "$SMALL" "$SMALL_TYPE" | sed -n 's/^TotalTime: *//p')
      drawn=$(fully_drawn 10)
      log "round $round $label: first frame ${total:-NA} ms, drawn $drawn ms"
      while read -r name value; do
        [ -n "$name" ] || continue
        PHASES["$label|$name"]+="$value"$'\n'
        PHASE_NAMES["$name"]=1
      done < <(step_lines)
      FIRST_FRAME[$label]+="${total:-NA}"$'\n'
      DRAWN[$label]+="$drawn"$'\n'
    done
  done
done

pss_of() {
  # The summary line reads "TOTAL PSS: 12345 ..." on current Android and the
  # table row "TOTAL 12345 ..." on older versions.
  adb shell dumpsys meminfo "$1" | tr -d '\r' |
    awk '/TOTAL PSS:/ { print $3; exit } /^ *TOTAL +[0-9]/ { print $2; exit }'
}

renderer_pss() {
  # WebView renderers are isolated processes; their PSS is read from the
  # system-wide "Total PSS by process" list rather than by pid.
  adb shell dumpsys meminfo | tr -d '\r' | awk '
    /Total PSS by process:/ { inside = 1; next }
    inside && /^ *$/ { inside = 0 }
    inside && /sandboxed_process/ { value = $1; gsub(/[,K:]/, "", value); total += value }
    END { print total + 0 }'
}

indexed_ms() {
  local limit=$1 line
  for _ in $(seq 1 $((limit * 10))); do
    line=$(adb logcat -d -s LeanRows:I | grep -o "indexed [0-9]* rows in [0-9]* ms" | head -n 1 || true)
    if [ -n "$line" ]; then
      awk '{ print $5 }' <<<"$line"
      return
    fi
    sleep 0.1
  done
  echo NA
}

renderer_count() {
  adb shell ps -A -o NAME | tr -d '\r' | grep -c sandboxed_process || true
}

# Java and native heap of the app process (PSS, KB), from the App Summary.
heaps_of() {
  adb shell dumpsys meminfo "$1" | tr -d '\r' |
    awk '/^ *Java Heap:/ { java = $3 } /^ *Native Heap:/ { native = $3 } END { print (java ? java : "NA"), (native ? native : "NA") }'
}

# Prints the values of the app's "page NAME N ms" lines since the log was
# cleared, once there are at least COUNT of them or LIMIT seconds have passed.
page_times() {
  local name=$1 count=$2 end=$((SECONDS + $3)) values
  while true; do
    values=$(adb logcat -d -v raw -s "${TIMING_TAG:-none}:I" | sed -n "s/^page $name \([0-9][0-9]*\) ms$/\1/p")
    if [ "$(grep -c . <<<"$values" || true)" -ge "$count" ] || [ "$SECONDS" -ge "$end" ]; then break; fi
    sleep 0.2
  done
  printf '%s\n' "$values"
}

# Presses a key combination, as on a hardware keyboard, and waits up to
# LIMIT seconds for the page to time one more piece of work called NAME.
press() {
  local name=$1 limit=$2 before
  shift 2
  before=$(page_times "$name" 0 0 | grep -c . || true)
  adb shell input keycombination "$@" >/dev/null 2>&1 || log "input keycombination $* failed"
  page_times "$name" $((before + 1)) "$limit" >/dev/null
}

# The app's "phase drawn N ms" line of the newest activity, or NA.
phase_drawn() {
  local limit=$1 value
  for _ in $(seq 1 $((limit * 5))); do
    value=$(adb logcat -d -v raw -s "${TIMING_TAG:-none}:I" | sed -n 's/^phase drawn \([0-9][0-9]*\) ms$/\1/p' | tail -n 1)
    if [ -n "$value" ]; then echo "$value"; return; fi
    sleep 0.2
  done
  echo NA
}

# Milliseconds from a light or dark switch to the page drawn in its new
# colours, whichever way the build handles it, or NA.
night_ms() {
  local limit=$1 log value
  for _ in $(seq 1 $((limit * 5))); do
    log=$(adb logcat -d -v raw -s "${TIMING_TAG:-none}:I")
    value=$(sed -n -e 's/^page night \([0-9][0-9]*\) ms$/\1/p' -e 's/^phase drawn \([0-9][0-9]*\) ms$/\1/p' <<<"$log" | tail -n 1)
    if [ -n "$value" ]; then echo "$value"; return; fi
    sleep 0.2
  done
  echo NA
}

# Milliseconds from a light or dark switch until the diagrams are drawn
# again: the page's own count for builds that recolour in place, the new
# activity's steps up to the document sent plus the page's render for
# builds that recreate it. NA if neither shows up.
night_diagrams_ms() {
  local limit=$1 log value sent render
  for _ in $(seq 1 $((limit * 5))); do
    log=$(adb logcat -d -v raw -s "${TIMING_TAG:-none}:I")
    value=$(sed -n 's/^page scheme-diagrams \([0-9][0-9]*\) ms$/\1/p' <<<"$log" | tail -n 1)
    if [ -n "$value" ]; then echo "$value"; return; fi
    sent=$(sed -n 's/^phase document sent \([0-9][0-9]*\) ms$/\1/p' <<<"$log" | tail -n 1)
    render=$(sed -n 's/^page render \([0-9][0-9]*\) ms$/\1/p' <<<"$log" | tail -n 1)
    if [ -n "$sent" ] && [ -n "$render" ]; then echo $((sent + render)); return; fi
    sleep 0.2
  done
  echo NA
}

# Page work on the open document: theme, zoom, rotation, a dark mode switch
# and find. The page times each from the key to the result on screen.
declare -A NIGHT_DIAGRAMS_MS=() NIGHT_SMALL_MS=()
declare -A THEME_MS=() ZOOM_MS=() WIDTH_MS=() RESIZE_MS=() NIGHT_MS=() FIND_MS=()
page_work() {
  local label=$1 before value
  adb logcat -c
  # Ctrl+Shift+T five times: through all five themes and back to the first.
  for _ in 1 2 3 4 5; do press theme 90 KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_T; done
  THEME_MS[$label]=$(page_times theme 5 0)
  # Two steps in and two out, back to the saved level.
  for key in EQUALS EQUALS MINUS MINUS; do press zoom 30 KEYCODE_CTRL_LEFT "KEYCODE_$key"; done
  ZOOM_MS[$label]=$(page_times zoom 4 0)
  # Ctrl+Shift+L three times: Wide, Full and back to Narrow (desktop 0.6.0).
  for _ in 1 2 3; do press width 15 KEYCODE_CTRL_LEFT KEYCODE_SHIFT_LEFT KEYCODE_L; done
  WIDTH_MS[$label]=$(page_times width 3 0)
  # Landscape and back, with auto-rotate off.
  adb shell settings put system accelerometer_rotation 0
  for rotation in 1 0; do
    before=$(page_times resize 0 0 | grep -c . || true)
    adb shell settings put system user_rotation "$rotation"
    page_times resize $((before + 1)) 30 >/dev/null
    sleep 3
  done
  RESIZE_MS[$label]=$(page_times resize 1 0)
  # Dark mode on and off. Builds before 0.2.0 let Android recreate the
  # activity, which logs its own steps up to drawn; later ones recolour in
  # place and log the time from the switch to the new colours on screen.
  for mode in yes no; do
    adb logcat -c
    adb shell cmd uimode night "$mode" >/dev/null
    value=$(night_ms 90)
    NIGHT_MS[$label]+="$value"$'\n'
    NIGHT_DIAGRAMS_MS[$label]+="$(night_diagrams_ms 90)"$'\n'
    sleep 3
  done
  # The same on a document without diagrams.
  open_document "$SMALL" "$SMALL_TYPE" >/dev/null
  fully_drawn 30 >/dev/null
  sleep 3
  for mode in yes no; do
    adb logcat -c
    adb shell cmd uimode night "$mode" >/dev/null
    NIGHT_SMALL_MS[$label]+="$(night_ms 60)"$'\n'
    sleep 3
  done
  open_document "$LARGE" "$LARGE_TYPE" >/dev/null
  fully_drawn 90 >/dev/null
  sleep 5
  # Find: Ctrl+F, then a word typed into the find field.
  adb logcat -c
  adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_F >/dev/null 2>&1 || log "input keycombination failed"
  sleep 1
  adb shell input text bullet
  FIND_MS[$label]=$(page_times find 1 30)
  adb shell input keyevent KEYCODE_ESCAPE
  log "$label page work: theme $(tr '\n' ' ' <<<"${THEME_MS[$label]}")ms; zoom $(tr '\n' ' ' <<<"${ZOOM_MS[$label]}")ms; width $(tr '\n' ' ' <<<"${WIDTH_MS[$label]}")ms; resize $(tr '\n' ' ' <<<"${RESIZE_MS[$label]}")ms; dark mode $(tr '\n' ' ' <<<"${NIGHT_MS[$label]}")ms; find $(tr '\n' ' ' <<<"${FIND_MS[$label]}")ms"
}

# Memory with the small file, to tell the WebView's own cost from the document's.
declare -A SMALL_APP=() SMALL_RENDERER=() SMALL_ALLOCATED=()
for label in "${LABELS[@]}"; do
  install "$label"
  open_document "$SMALL" "$SMALL_TYPE" >/dev/null
  fully_drawn 30 >/dev/null
  sleep 5
  SMALL_APP[$label]=$(pss_of "$PKG")
  SMALL_ALLOCATED[$label]=$(adb logcat -d -v raw -s "${TIMING_TAG:-none}:I" | sed -n 's/^allocated \([0-9][0-9]*\) KB by drawn$/\1/p' | head -n 1)
  SMALL_RENDERER[$label]=0
  if [ "$WEBVIEW" = yes ]; then SMALL_RENDERER[$label]=$(renderer_pss); fi
  log "$label small file: app ${SMALL_APP[$label]:-NA} KB, renderer ${SMALL_RENDERER[$label]} KB in $(renderer_count) renderer process(es)"
done

# The fixture provider writes generated files into its cache the first time.
open_document "$LARGE" "$LARGE_TYPE" >/dev/null
sleep 20

declare -A APP_PSS=() RENDERER_PSS=() TOTAL_PSS=() LARGE_DRAWN=() INDEXED=() JANK=() FRAMES=() P90=()
declare -A ALLOCATED=() JAVA_HEAP=() NATIVE_HEAP=() LARGE_PHASES=() LARGE_PHASE_NAMES=()
for label in "${LABELS[@]}"; do
  install "$label"
  for run in 1 2 3; do
    open_document "$LARGE" "$LARGE_TYPE" >/dev/null
    drawn=$(fully_drawn 90)
    indexed=NA
    if [ "$MEASURE_INDEXING" = yes ]; then indexed=$(indexed_ms 60); fi
    sleep 5
    app=$(pss_of "$PKG")
    renderer=0
    if [ "$WEBVIEW" = yes ]; then renderer=$(renderer_pss); fi
    total=$(plus "${app:-NA}" "$renderer")
    read -r java native < <(heaps_of "$PKG")
    allocated=$(adb logcat -d -v raw -s "${TIMING_TAG:-none}:I" | sed -n 's/^allocated \([0-9][0-9]*\) KB by drawn$/\1/p' | head -n 1)
    while read -r name value; do
      [ -n "$name" ] || continue
      LARGE_PHASES["$label|$name"]+="$value"$'\n'
      LARGE_PHASE_NAMES["$name"]=1
    done < <(step_lines)
    log "$label large run $run: drawn $drawn ms, indexed $indexed ms, app ${app:-NA} KB (Java heap $java, native heap $native), renderer $renderer KB in $(renderer_count) process(es), total $total KB, Java allocated ${allocated:-NA} KB"
    ALLOCATED[$label]+="${allocated:-NA}"$'\n'
    JAVA_HEAP[$label]+="$java"$'\n'
    NATIVE_HEAP[$label]+="$native"$'\n'
    LARGE_DRAWN[$label]+="$drawn"$'\n'
    INDEXED[$label]+="$indexed"$'\n'
    APP_PSS[$label]+="${app:-NA}"$'\n'
    RENDERER_PSS[$label]+="$renderer"$'\n'
    TOTAL_PSS[$label]+="$total"$'\n'
  done

  # Scrolling: ten swipes through the large file, as a reader would. adb input
  # goes to the window with focus. When the emulator's launcher has kept focus
  # in front of the app, the swipes would miss it, so nothing is reported.
  focus=$(adb shell dumpsys window | tr -d '\r' | grep -m 1 'mCurrentFocus=' || true)
  if ! grep -q "$PKG" <<<"$focus"; then
    log "$label scrolling not measured, another window has focus: $focus"
    FRAMES[$label]="not measured"
    JANK[$label]="not measured (app not in front)"
    P90[$label]=NA
    continue
  fi
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
  for _ in $(seq 1 10); do
    adb shell input swipe 540 1800 540 600 250
    sleep 0.5
  done
  gfx=$(adb shell dumpsys gfxinfo "$PKG" | tr -d '\r')
  FRAMES[$label]=$(sed -n 's/^Total frames rendered: *//p' <<<"$gfx" | head -n 1)
  JANK[$label]=$(sed -n 's/^Janky frames: *//p' <<<"$gfx" | head -n 1)
  P90[$label]=$(sed -n 's/^90th percentile: *//p' <<<"$gfx" | head -n 1)
  log "$label scrolling: frames ${FRAMES[$label]:-NA}, janky ${JANK[$label]:-NA}, p90 ${P90[$label]:-NA}"
  if [ "$WEBVIEW" = yes ]; then page_work "$label"; fi
done

row() {
  local name=$1; shift
  printf '| %s |' "$name"
  for value in "$@"; do printf ' %s |' "${value:-NA}"; done
  printf '\n'
}

{
  printf '| Measure |'; for label in "${LABELS[@]}"; do printf ' %s |' "$label"; done; printf '\n'
  printf '| --- |'; for _ in "${LABELS[@]}"; do printf ' ---: |'; done; printf '\n'
  sizes=(); first=(); first_net=(); drawn=(); drawn_net=(); large=(); indexed=(); app=(); renderer=(); total=(); frames=(); jank=(); p90=()
  for label in "${LABELS[@]}"; do
    sizes+=("$(stat -c %s "${APKS[$label]}")")
    f=$(median <<<"${FIRST_FRAME[$label]}")
    d=$(median <<<"${DRAWN[$label]}")
    first+=("$f"); first_net+=("$(minus "$f" "$EMPTY_FIRST")")
    drawn+=("$d"); drawn_net+=("$(minus "$d" "$EMPTY_FIRST")")
    large+=("$(median <<<"${LARGE_DRAWN[$label]}")")
    indexed+=("$(median <<<"${INDEXED[$label]}")")
    app+=("$(median <<<"${APP_PSS[$label]}")")
    renderer+=("$(median <<<"${RENDERER_PSS[$label]}")")
    total+=("$(median <<<"${TOTAL_PSS[$label]}")")
    frames+=("${FRAMES[$label]:-NA}")
    jank+=("${JANK[$label]:-NA}")
    p90+=("${P90[$label]:-NA}")
  done
  row "Release APK, all ABIs (bytes)" "${sizes[@]}"
  row "Cold start to first frame, $SMALL_NAME (ms, median of $STARTS)" "${first[@]}"
  row "**First frame minus the empty app** (ms)" "${first_net[@]}"
  row "Cold start to $DRAWN_WHAT drawn, $SMALL_NAME (ms, median of $STARTS)" "${drawn[@]}"
  row "**Drawn minus the empty app's first frame** (ms)" "${drawn_net[@]}"
  row "$LARGE_NAME drawn after a cold start (ms, median of 3)" "${large[@]}"
  if [ "$MEASURE_INDEXING" = yes ]; then row "Indexing all rows of $LARGE_NAME (ms, median of 3)" "${indexed[@]}"; fi
  small_app=(); small_renderer=(); small_total=()
  for label in "${LABELS[@]}"; do
    small_app+=("${SMALL_APP[$label]:-NA}")
    small_renderer+=("${SMALL_RENDERER[$label]:-NA}")
    small_total+=("$(plus "${SMALL_APP[$label]:-NA}" "${SMALL_RENDERER[$label]:-0}")")
  done
  if [ "$WEBVIEW" = yes ]; then
    row "Total PSS after $SMALL_NAME, app and renderer (KB)" "${small_total[@]}"
  else
    row "App process PSS after $SMALL_NAME (KB)" "${small_app[@]}"
  fi
  row "App process PSS after $LARGE_NAME (KB, median of 3)" "${app[@]}"
  if [ "$WEBVIEW" = yes ]; then
    row "WebView renderer PSS after $LARGE_NAME (KB, median of 3)" "${renderer[@]}"
    row "**Total PSS, app and renderer** (KB, median of 3)" "${total[@]}"
  fi
  row "Frames over 10 swipes (software GPU, not a budget check)" "${frames[@]}"
  row "Janky frames over 10 swipes (software GPU, not a budget check)" "${jank[@]}"
  row "90th percentile frame time (software GPU, not a budget check)" "${p90[@]}"
  if [ "$WEBVIEW" = yes ]; then
    small_allocated=(); allocated=(); java=(); native=(); theme=(); zoom=(); width=(); resize=(); night=(); night_diagrams=(); night_small=(); find=()
    for label in "${LABELS[@]}"; do
      small_allocated+=("${SMALL_ALLOCATED[$label]:-NA}")
      allocated+=("$(median <<<"${ALLOCATED[$label]:-}")")
      java+=("$(median <<<"${JAVA_HEAP[$label]:-}")")
      native+=("$(median <<<"${NATIVE_HEAP[$label]:-}")")
      theme+=("$(median <<<"${THEME_MS[$label]:-}")")
      zoom+=("$(median <<<"${ZOOM_MS[$label]:-}")")
      width+=("$(median <<<"${WIDTH_MS[$label]:-}")")
      resize+=("$(median <<<"${RESIZE_MS[$label]:-}")")
      night+=("$(median <<<"${NIGHT_MS[$label]:-}")")
      night_diagrams+=("$(median <<<"${NIGHT_DIAGRAMS_MS[$label]:-}")")
      night_small+=("$(median <<<"${NIGHT_SMALL_MS[$label]:-}")")
      find+=("$(median <<<"${FIND_MS[$label]:-}")")
    done
    row "Java allocations from start to $SMALL_NAME drawn (KB)" "${small_allocated[@]}"
    row "Java allocations from start to $LARGE_NAME drawn (KB, median of 3)" "${allocated[@]}"
    row "Java heap after $LARGE_NAME (KB PSS, median of 3)" "${java[@]}"
    row "Native heap after $LARGE_NAME (KB PSS, median of 3)" "${native[@]}"
    row "Theme switch on $LARGE_NAME, key to drawn (ms, median of 5)" "${theme[@]}"
    row "Zoom step on $LARGE_NAME, key to drawn (ms, median of 4)" "${zoom[@]}"
    row "Text width step on $LARGE_NAME, key to drawn (ms, median of 3; phone held upright)" "${width[@]}"
    row "Find on $LARGE_NAME, last key to match count, with the page's 120 ms pause (ms)" "${find[@]}"
    row "Rotation on $LARGE_NAME, resize to drawn (ms, median)" "${resize[@]}"
    row "Dark mode switch to $LARGE_NAME drawn in the new colours (ms, median of 2)" "${night[@]}"
    row "Dark mode switch on $LARGE_NAME until its diagrams are drawn again (ms, median of 2)" "${night_diagrams[@]}"
    row "Dark mode switch to $SMALL_NAME drawn in the new colours (ms, median of 2)" "${night_small[@]}"
  fi
  steps() {
    local -n values=$1 names=$2
    printf '| Step |'; for label in "${LABELS[@]}"; do printf ' %s |' "$label"; done; printf '\n'
    printf '| --- |'; for _ in "${LABELS[@]}"; do printf ' ---: |'; done; printf '\n'
    for name in "webview_created" "file_read" "rendered" "page_loaded" "reader_ready" "document_sent" "drawn"; do
      [ -n "${names[$name]:-}" ] || continue
      printf '| %s |' "${name//_/ }"
      for label in "${LABELS[@]}"; do printf ' %s |' "$(median <<<"${values[$label|$name]:-}")"; done
      printf '\n'
    done
    echo
    echo "Inside the page: page-dom and page-load are ms from the page's own start to"
    echo "DOMContentLoaded and the load event; render is ms from the document message"
    echo "to the document on screen."
    echo
    printf '| Page step |'; for label in "${LABELS[@]}"; do printf ' %s |' "$label"; done; printf '\n'
    printf '| --- |'; for _ in "${LABELS[@]}"; do printf ' ---: |'; done; printf '\n'
    for name in "page-dom" "page-load" "render"; do
      [ -n "${names[$name]:-}" ] || continue
      printf '| %s |' "$name"
      for label in "${LABELS[@]}"; do printf ' %s |' "$(median <<<"${values[$label|$name]:-}")"; done
      printf '\n'
    done
  }
  if [ "${#PHASE_NAMES[@]}" -gt 0 ]; then
    echo
    echo "Where the time goes when $SMALL_NAME is opened: milliseconds from the activity's"
    echo "creation to each step, as the app logs them (median of $STARTS). The file is"
    echo "read and rendered on a worker thread while the WebView starts."
    echo
    steps PHASES PHASE_NAMES
  fi
  if [ "${#LARGE_PHASE_NAMES[@]}" -gt 0 ]; then
    echo
    echo "The same for $LARGE_NAME (median of 3)."
    echo
    steps LARGE_PHASES LARGE_PHASE_NAMES
  fi
  echo
  echo "Empty app on the same emulator: $EMPTY_FIRST ms to first frame (median of $STARTS)."
  echo "Start time and jank on this emulator are not the budget check: it has no GPU,"
  echo "and an empty app alone takes the time above. The budget needs a real phone;"
  echo "see the phone test in PROGRESS.md."
}
