# Progress

Updated with every merge. The next session starts here.

## Done

- Gradle project: Kotlin, classic Views, no AndroidX or other libraries in
  the app. compileSdk and targetSdk 36, minSdk 26.
- Manifest: no permissions; open-with filters for Markdown (VIEW and SEND);
  WebView metrics and Safe Browsing off; backup off.
- Reader (PR #2): the desktop reader page and the desktop's C++ Markdown
  core (md4c) through JNI. Requests other than the listed reader files get
  an empty 403; navigation is refused; content:// only; 32 MiB limit;
  section and whole-file copy; back closes the diagram viewer; rotation
  keeps the place; a crashed renderer is replaced.
- Themes (PR #3): System, Light, Dark, Dim and Dusk from desktop 0.5.0,
  with the theme button shown on phones.
- CI: lint, JVM tests, the desktop core's tests (ctest), release APK and
  bundle, the permission check, instrumented tests on API 26 (WebView 69)
  and API 34 (WebView 113), budgets against the base branch and an empty
  app, APK contents, the arm64 download size and a native-library
  breakdown. Mutation check: `mutations.yml` with `mutations/*.json`.
- Store texts, data safety, content rating, closed-test plan and light and
  dark screenshots in `store/`; signing steps in the README.

## In progress

- Release v0.1.0.

## Remaining

- The phone test below, on real hardware.
- Play Console setup and the closed test (see `store/closed-test.md`);
  these need the maintainer's account.

## Known gaps

- Images next to the Markdown file and links to other local Markdown files
  are not shown: a shared file comes without access to its folder.
- WebView 69 to 104 shows no match count in Find; Enter still finds the
  next match. Mermaid needs WebView 94.
- The WebView renderer uses about 270 to 300 MB after a 1 MiB document with
  11 Mermaid diagrams.
- On a phone the desktop toolbar is wider than the screen, so the Open
  button is cut at the right edge (see `store/screenshots`).
- Half of the native library is the statically linked C++ runtime.
- Release builds from CI are signed with a throwaway key that changes with
  every run, so one release cannot be installed over another; uninstall
  first.
- The emulator has no GPU, so start time and jank from CI are not the
  budget check, and in some runs the scrolling measure did not register
  frames at all.

## Measured numbers

From the budgets job on an API 34 emulator (SwiftShader, no GPU), PR #3,
run 40838486045. The empty app took 1,987 ms to its first frame there.

| Measure | Value |
| --- | ---: |
| Release APK, all ABIs | 4,102,729 bytes |
| What Play delivers to an arm64-v8a phone | 2,311,362 bytes installed, 1,496,762 download |
| First frame minus the empty app, simple.md | 187 ms |
| Document drawn minus the empty app's first frame, simple.md | 1,312 ms |
| large.md drawn after a cold start | 6,304 ms |
| Total PSS after simple.md, app and renderer | 114,096 KB |
| App PSS after large.md | 77,758 KB |
| WebView renderer PSS after large.md | 267,196 KB |
| Total PSS after large.md | 344,836 KB |

Where the start time goes with simple.md (ms from the activity's
creation): WebView created 819, page loaded 2,332, document sent 2,349,
document drawn 2,895.

## Phone test

The emulator in CI has no GPU, so its start times and jank do not show what
a phone does. This is the check to run on a real phone. It takes about ten
minutes.

### Install

From the release page on GitHub, download onto the phone:

- `LeanMark-0.1.0.apk`: the release build (minified), signed with a throwaway
  key. Use this one for timing.
- `LeanMark-0.1.0-debug.apk`: the debug build, for comparison only. It is slower
  by design.
- `empty-app.apk`: an app with one empty screen, for the start-time
  baseline with adb (it has no launcher icon).
- `LeanMark-0.1.0-phone-test-files.zip`: the files below. Unzip it into
  Downloads.

Open the APK from the browser's downloads or the Files app and allow the
install when Android asks. Both APKs have the same package name, so the
second replaces the first. To check a download, compare it with its
`.sha256` file.

### Start time

1. Settings > Apps > LeanMark > Force stop.
2. Start a screen recording (Quick Settings > Screen record), then in the
   Files app tap `simple.md` in Downloads and choose LeanMark.
3. Stop the recording when the text appears. Step through the recording frame
   by frame and note the time from the tap to the text on screen.
4. Repeat steps 1 to 3 three times and keep the middle value.
5. Do the same with an app that does very little (for example the
   Calculator), and subtract its time: that difference is the number the
   budget means. The budget is under 1 second.

With a computer and adb the system measures it:

```sh
# Start from the launcher icon to the first frame (TotalTime, in ms):
adb shell am force-stop io.github.abooodbah.leanmark
adb shell am start-activity -W -n io.github.abooodbah.leanmark/.MainActivity

# Opening simple.md: force-stop, clear the log, open the file from the
# Files app as in step 2, then read the time the app reports when the
# content is on screen:
adb shell am force-stop io.github.abooodbah.leanmark
adb logcat -c
adb logcat -d | grep "Fully drawn io.github.abooodbah.leanmark"
```

For the baseline, install `empty-app.apk` from the same release (one
empty screen, no libraries; the same app CI subtracts) and time it the same
way:

```sh
adb install empty-app.apk
adb shell am force-stop io.github.abooodbah.leanmark.baseline
adb shell am start-activity -W -n io.github.abooodbah.leanmark.baseline/.EmptyActivity
```

### Scrolling

1. Settings > System > Developer options > Profile HWUI rendering > On
   screen as bars. (Developer options appear after tapping Build number
   seven times in Settings > About phone.)
2. Open `large.md`. Scroll from the top to the end of large.md with quick flicks.
3. Note how often bars cross the green line, and whether anything stutters
   or shows blank areas.

With adb: `adb shell dumpsys gfxinfo io.github.abooodbah.leanmark reset`, scroll,
then `adb shell dumpsys gfxinfo io.github.abooodbah.leanmark` and note "Janky
frames" and "90th percentile".

### Memory

After scrolling `large.md`: `adb shell dumpsys meminfo io.github.abooodbah.leanmark`
(TOTAL PSS), and for the WebView renderer `adb shell dumpsys meminfo | grep -A40 "Total PSS by process" | grep sandboxed_process`.

### What to send back

Phone model, Android version, the three start times and the empty-app time,
what the scrolling bars looked like, and the memory numbers if you used adb.
