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
- Phone toolbar (PR #5): Find, Copy, theme and Open fit one row from 320 dp.
- Store texts, data safety, content rating, closed-test plan, Play graphics
  and screenshots in `store/`; fastlane metadata; a GitHub Pages site for
  the closed test; signing steps in the README.
- Releases: v0.1.0 and v0.1.1 (pre-releases, throwaway key) and v0.1.2
  (2026-10-30, the first APK signed with the stable release key). The
  "Release files" workflow attaches the APK, the bundle and their sums; the
  "Play bundle" workflow builds the upload-key bundle as an artifact.
- CI: lint, JVM tests, the desktop core's tests (ctest), release APK and
  bundle, the permission check, instrumented tests on API 26 (WebView 69)
  and API 34 (WebView 113), budgets against the base branch and an empty
  app, APK contents, the arm64 download size and a native-library
  breakdown. Mutation check: `mutations.yml` with `mutations/*.json`.
- 0.2.0 plan (PR #15), below.
- Smaller APK: the native library exports only its JNI entry points (PR
  #17, APK 4,103,101 to 2,253,125 bytes) and the APK carries no Kotlin
  metadata (PR #18, 13,728 bytes less). CI checks both.
- Page work timed in the budgets job (PR #16): theme, zoom, find, rotation,
  dark mode and the steps of opening a file, with Java allocations. Mutation
  entries can name the test classes to run.
- Fewer copies when a file opens: a file of known size is read straight
  into its array (PR #21) and the document message is built once from the
  rendered bytes (PR #22). Java allocations up to large.md on screen went
  from 13,461 to 12,350 KB (#21, run 41993382698) and from 13,823 to
  10,111 KB (#22, run 41993441975).
- Desktop 0.6.0 port (PR #19): reader files and core from desktop v0.6.0
  (3208af8). Text width (Narrow, Wide, Full) saved as `width` and sent with
  the saved zoom in the host message; settings from 0.1.x open in Narrow.
  Zoom enlarges only the document: WebView text zoom follows only the
  system font size, the page zooms the document, a pinch steps through the
  same levels (or zooms an expanded figure), and a saved zoom is snapped to
  the levels. On a phone the settings panel is a sheet at the bottom
  without the shortcut list, the theme button shows only its dot, and back
  closes the sheet. The missing-image note takes the theme's colours.

## In progress

- Version 0.2.0. The plan is below. Next: "On this page" as a sheet on
  phones, then the section highlight from desktop LeanMark.
- PR #19 is green but not merged until rotation is back within 10% of
  main: rotating large.md is about four times slower with the 0.6.0 reader
  (137 to 221 ms against 515 to 686 ms, bisect run 41988918132). The cause
  is in the desktop reader, which desktop LeanMark is fixing.

## Plan for 0.2.0

The work goes in this order. Each step is its own pull request, merged when
CI is green, and this file is updated with every merge.

1. Efficiency review. Time what runs on open, scroll, find, zoom, theme
   switch and rotation, with release builds in the budgets job, and record
   what the app allocates and keeps. The findings go in the table below.
   Clear wins are fixed one per pull request, with numbers before and after.
   The rest is listed for the maintainer to decide.
2. Desktop 0.6.0 port. The reader files come from desktop tag v0.6.0 with
   `scripts/update-reader-assets.sh`. The Android side:
   - Text width: Narrow, Wide and Full. The app saves the choice and sends it
     with the host message. Settings from 0.1.x have no width and open in
     Narrow, as on the desktop.
   - Zoom that enlarges only the document. The WebView's text zoom follows
     only the system font size; the reader zooms the document with CSS zoom.
     A zoom level saved by 0.1.x loads at the same text size. Pinch zoom
     steps through the same levels instead of magnifying the whole screen,
     so the toolbar keeps its size. A saved level outside the range gives
     100%.
   - Settings button and sheet with theme, text width and zoom. On a phone
     (up to 560 px wide) the desktop panel becomes a sheet at the bottom of
     the screen; on wider screens it sits beside the document as on the
     desktop. Back closes it.
   - The coloured theme dot. On a phone the theme button shows only the dot
     so the toolbar keeps one row; its accessible name says the theme.
   - The missing-image note in theme colours, which comes with reader.css.
3. Section highlight in "On this page". Desktop LeanMark adds it first
   (0.7.0); the reader files are then updated from that commit and checked
   on the emulator in all five themes. The desktop shows the outline only
   from 1180 px wide, so a phone shows none. On a phone the outline opens as
   a sheet instead, with the current section highlighted and kept in view.
4. Version 0.2.0: version bump, F-Droid changelog, annotated tag v0.2.0, a
   GitHub pre-release with plain notes, then the "Release files" workflow
   for the APK, the bundle and their sums, and the "Play bundle" workflow.
   Nothing is uploaded to Google Play.

Every change keeps the stack (Kotlin, classic Views, WebView), adds no
library to the app, and keeps the app without permissions.

## Desktop features the app lacks

From the desktop CHANGELOG.md and docs/releases/ for releases after
2026-10-29. Desktop 0.6.0 (2026-10-31) is the only one so far; 0.7.0 is in
progress.

| Desktop feature | Desktop version | Android plan |
| --- | --- | --- |
| Text width: Narrow, Wide, Full, remembered | 0.6.0 | Port (step 2). On a phone held upright all three look the same, because the screen is narrower than the 720 px column; they differ in landscape, on tablets and when zoomed out. |
| Settings button and panel: theme, width, zoom | 0.6.0 | Port (step 2) as a sheet on phones. |
| Shortcut list in the settings panel, Ctrl+/ and F1 | 0.6.0 | Skipped on Android. Phones have no keyboard. With a hardware keyboard the reader's shortcuts still work, including Ctrl+/ for the sheet. |
| Theme dot, tooltip and accessible name | 0.6.0 | Port (step 2). WebView shows no tooltips; the accessible name carries the theme. |
| Zoom enlarges only the document | 0.6.0 | Port (step 2), including pinch. |
| Missing-image note in theme colours | 0.6.0 | Port (step 2). |
| Current section highlighted in "On this page" | 0.7.0 (in progress) | Port (step 3), with an outline sheet on phones. |

Older desktop features the app still lacks, and why:

| Desktop feature | Why not on Android |
| --- | --- |
| Tabs (0.3.0) | One document per screen is the Android pattern; recent apps switch between them. |
| Images next to the file and links to other local files | A shared file comes without access to its folder. |
| Watching the file while it is open | The app checks the file's size and time when it comes back to the front, and reloads if they changed. |
| Installer, file associations, winget, Scoop, Chocolatey, Microsoft Store | Windows only. Android uses the open-with filters and Google Play. |
| macOS menu shortcuts | macOS only. |

## Efficiency review

Method: release builds in the budgets job on an API 34 emulator (x86_64,
4 cores, SwiftShader, no GPU), main and the branch measured the same way in
turns, with an empty app's start time subtracted. The emulator has no GPU,
so jank and start times there are not the phone budget; they compare builds.
Since PR #16 the job also times page work on large.md (1 MiB, 11 Mermaid
diagrams) with keys as from a hardware keyboard: the page measures from the
key to the result on screen, and the app logs it under `LeanMarkTiming`.
Sizes come from the build job.

Findings. Numbers are from run 41981714168 (PR #16) unless a PR is named.

| Place | What it costs | Measured | Proposed fix | Status |
| --- | --- | ---: | --- | --- |
| Native library (`CMakeLists.txt`) | The C++ runtime exported 1,975 symbols, so the linker kept all of its code | APK 4,103,101 bytes; arm64 `.so` 1,113,648 bytes | Export only the JNI entry points (version script) | Fixed in #17: APK 2,253,125 bytes, arm64 `.so` 411,128, arm64 download 1,497,097 to 1,294,523 |
| APK (`build.gradle.kts`) | Kotlin metadata files nothing reads | 13,728 bytes | Leave them out of packaging | Fixed in #18 |
| Theme switch (`reader.js`) | Every Mermaid diagram is drawn again in the new colours | 5,228 ms on large.md (11 diagrams) | Desktop reader code. Desktop LeanMark plans to skip the redraw when a theme change keeps the diagram colours; it comes with the next reader update | For the desktop |
| Zoom (`WebSettings.textZoom`) | Enlarges the toolbar too and lays the whole page out again | 786 ms per step on large.md | The reader zooms only the document (desktop 0.6.0) | Replaced in #19: 1,405 to 1,144 ms per step (run 41987196578) |
| Dark mode switch (manifest `configChanges`) | Android recreates the activity: the file is read, parsed and drawn again | 1,245 ms to drawn on large.md, against 167 ms for a rotation | Handle `uiMode` in the activity. Needs a check on a phone that WebView's `prefers-color-scheme` follows without a new activity | For the maintainer to decide |
| Opening large.md in the page | Mermaid draws 11 diagrams after the text is on screen | Text drawn 1,349 ms after creation; diagrams done 3,561 ms after the document arrives | Desktop reader code (Mermaid). The text is readable before the diagrams | No change |
| WebView renderer memory | DOM of a 1 MiB document plus Mermaid | 314,661 KB PSS after large.md | Desktop reader code | No change |
| Open: port posted in `onPageFinished` | The document waits for the page's load event | Load event 15 ms after DOMContentLoaded (292 and 277 ms) | Posting the port earlier would save at most 15 ms | No change |
| Open: provider queries and file read (`displayName`, `querySize`, `stampOf`) | Four content provider queries per open | File read and parsed 65 to 66 ms after creation for simple.md, 60 ms for large.md, about 400 ms before the page is ready | They run on the worker while the WebView starts, so they do not delay the first page | No change |
| Open: Java copies (`readLimited`, `load`, `HostMessages.document`) | A second copy of the file, and the rendered HTML held twice as UTF-16 text until it is sent | Java allocations up to the first draw: 13,634 KB for large.md against 3,937 KB for simple.md (run 41983948818); Java heap 1,328 KB PSS afterwards | One copy fewer of each | Fixed in #21 (13,461 to 12,350 KB) and #22 (13,823 to 10,111 KB) |
| Find (`reader.js`) | Search over the whole article after a 120 ms pause | 323 ms on large.md, last key to count | Desktop reader code | No change |
| Rotation | Handled without a new activity | 167 ms from resize to drawn on large.md | None | No change |
| Scrolling | Reading progress once per frame (desktop 0.4.0) | 81% janky frames over 10 swipes on base and branch alike (software GPU) | Needs a phone with a GPU to judge | Phone test |
| Dead code | `MarkdownCore.normalizeZoom` was never called, so a bad saved zoom was used as is; the desktop file helpers in the core | The file helpers are no longer in the library after #17 | Use `normalizeZoom` for the saved zoom | Fixed in the 0.6.0 port |
| Dependencies | None in the app besides the Kotlin standard library, which R8 shrinks to a 52,512-byte `classes.dex`. Test only: JUnit, org.json, AndroidX Test | | None | No change |

"For the maintainer to decide" rows are proposals: they change behaviour or
need a phone to verify, so they are not clear wins.

## Remaining

- The phone test below, on real hardware.
- Play Console setup and the closed test (see `store/closed-test.md`);
  these need the maintainer's account.

## Known gaps

- Rotating a large document lays it out twice with the 0.6.0 reader: on
  the emulator, large.md took 515 to 686 ms from resize to drawn against
  137 to 221 ms with the 0.5.0 reader (bisect run 41988918132). The cause
  is in the desktop reader, which sets the layout attribute in its resize
  handler after Blink has already laid the page out at the new size.
  Reported for the desktop.
- On a phone held upright the three text widths look the same: the screen
  is narrower than the 720 px column. They differ in landscape, on tablets
  and when zoomed out.
- With the settings sheet open at a zoom under 75%, the page uses its
  compact layout, because the desktop reader counts the sheet's full width
  as taken.
- WebView 69 (Android 8 images) lays out the sheet's radio buttons one per
  line; it has no flex layout for fieldsets.

- Images next to the Markdown file and links to other local Markdown files
  are not shown: a shared file comes without access to its folder.
- WebView 69 to 104 shows no match count in Find; Enter still finds the
  next match. Mermaid needs WebView 94.
- The WebView renderer uses about 310 to 325 MB after a 1 MiB document with
  11 Mermaid diagrams.
- The statically linked C++ runtime is 129,778 of the arm64 library's
  411,128 bytes; most of the rest is md4c and its entity table.
- The emulator has no GPU, so start time and jank from CI are not the
  budget check, and in some runs the scrolling measure did not register
  frames at all.

## Measured numbers

From the budgets job on an API 34 emulator (SwiftShader, no GPU) for PR #14,
run 41019995503, main at the time ("base"). The empty app took 597 ms to
its first frame there.

| Measure | Value |
| --- | ---: |
| Release APK, all ABIs | 4,103,101 bytes |
| Release bundle | 3,389,425 bytes |
| What Play delivers to an arm64-v8a phone | 2,311,734 bytes installed, 1,497,097 download |
| First frame minus the empty app, simple.md | 59 ms |
| Document drawn minus the empty app's first frame, simple.md | 292 ms |
| large.md drawn after a cold start | 2,680 ms |
| Total PSS after simple.md, app and renderer | 114,263 KB |
| App PSS after large.md | 79,139 KB |
| WebView renderer PSS after large.md | 323,268 KB |
| Total PSS after large.md | 402,262 KB |

Where the start time goes with simple.md (ms from the activity's
creation): WebView created 215, page loaded 622, reader ready and document
sent 623, document drawn 694.

Tests on main: 26 JVM tests, the desktop core's test program (7 groups),
38 instrumented tests on API 34 (all pass) and on API 26 (35 pass, 3 need
WebView 94 and are skipped).

## Phone test

The emulator in CI has no GPU, so its start times and jank do not show what
a phone does. This is the check to run on a real phone. It takes about ten
minutes.

### Install

From the release page on GitHub, download onto the phone:

- `LeanMark-0.1.2.apk`: the release build (minified), signed with the
  release key. Use this one for timing.
- `LeanMark-0.1.2-debug.apk`: the debug build, for comparison only. It is
  slower by design.
- `empty-app.apk`: an app with one empty screen, for the start-time
  baseline with adb (it has no launcher icon).
- `LeanMark-0.1.2-phone-test-files.zip`: the files below. Unzip it into
  Downloads.

Open the APK from the browser's downloads or the Files app and allow the
install when Android asks. The release and debug APKs are signed with
different keys, so uninstall one before installing the other. To check a
download, compare it with its `.sha256` file.

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
