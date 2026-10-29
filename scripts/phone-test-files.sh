#!/usr/bin/env bash
# Writes the documents for the phone test in PROGRESS.md into one zip:
#   scripts/phone-test-files.sh out.zip
# large.md is built the way the instrumented tests' provider builds it: the
# desktop repositories' own documentation, in name order, repeated to 1 MiB.
set -euo pipefail
out=$(realpath -m "$1")
work=$(mktemp -d)
fixtures=app/src/androidTest/assets
cp "$fixtures/fixtures/simple.md" "$fixtures/fixtures/showcase.md" "$work/"
: > "$work/large.md"
while [ "$(stat -c %s "$work/large.md")" -lt 1048576 ]; do
  for file in $(ls "$fixtures/real" | sort); do
    cat "$fixtures/real/$file" >> "$work/large.md"
    printf '\n\n' >> "$work/large.md"
  done
done
(cd "$work" && zip -q -X "$out" simple.md showcase.md large.md)
rm -rf "$work"
