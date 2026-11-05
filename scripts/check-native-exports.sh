#!/usr/bin/env bash
# Fails when the release APK's native library exports anything besides the
# app's JNI entry points. Every exported symbol is kept by the linker even
# when nothing calls it, so a C++ runtime that leaks its symbols into the
# export table also keeps all of its code. Run after assembleRelease.
set -euo pipefail

ndk=$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)
nm="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"
apk=${1:-app/build/outputs/apk/release/app-release.apk}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -o "$apk" 'lib/*' -d "$work"

status=0
for lib in "$work"/lib/*/*.so; do
  abi=$(basename "$(dirname "$lib")")
  "$nm" -D --defined-only --format=just-symbols "$lib" | sort > "$work/exports.txt"
  extra=$(grep -v -E '^(Java_io_github_abooodbah_leanmark_[A-Za-z0-9_]+|JNI_OnLoad)$' "$work/exports.txt" || true)
  echo "$abi: $(wc -l < "$work/exports.txt") exported symbols, $(grep -c . <<<"$extra" || true) of them not JNI entry points"
  if [ -n "$extra" ]; then
    head -n 5 <<<"$extra" | sed 's/^/  /'
    status=1
  fi
done
if [ "$status" -ne 0 ]; then
  echo "::error::The native library exports symbols other than its JNI entry points."
fi
exit "$status"
