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
- Build provenance attestations (PR #33): the "Release files" job may
  request an OIDC token and write attestations, and runs
  `actions/attest-build-provenance` v4.2.2 (pinned to 4d10147) on
  `LeanMark-<version>.apk` and `.aab` after they are signed, checked and
  hashed, before they are attached. The release is created as a draft and
  published after the run. `scripts/check-release-workflow.py` checks this
  in CI, since the workflow runs only for a tag; it has not run for a tag
  yet. The README says how to check a download with `gh attestation
  verify`.
- CI: lint, JVM tests, the desktop core's tests (ctest), release APK and
  bundle, the permission check, instrumented tests on API 26 (WebView 69)
  and API 34 (WebView 113), budgets against the base branch and an empty
  app, APK contents, the arm64 download size and a native-library
  breakdown. Mutation check: `mutations.yml` with `mutations/*.json`.
- Two test waits made sturdier after failures with no app change on the
  API 34 emulator: HighlightTest scrolls again until the page stays at
  its target (PR #35), and a swipe that the WebView drops after a
  document appears is retried, up to three times (PR #37).
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
  Reader files later from desktop 1e9f20e, whose resize lays the page out
  once: rotating large.md 155 and 165 ms against 452 and 172 ms on main
  (run 42175760521).
- "On this page" as a sheet on phones (PR #24) and the current-section
  highlight from desktop LeanMark (PR #25), with the desktop's look in all
  five themes, beside the document on a wide screen and in the sheet on a
  phone.
- Version 0.2.0 (versionCode 4): notes in `docs/releases/v0.2.0.md` and
  `fastlane/metadata/android/en-US/changelogs/4.txt`.
- Light and dark switch in place (PR #28): the activity handles `uiMode`,
  so it keeps the WebView, the document and the position; only the System
  theme recolours. reader.js's diagram redraw starts two frames later, so
  the new colours show first. large.md: first frame 1,369 to 1,198 ms,
  diagrams 4,550 to 3,228 ms; simple.md 518 to 387 ms (run 42215871681).
- Start time checked (closed draft #27, run 42183359661): 15 cold starts of
  simple.md per build, builds in turns. Median to the document on screen:
  main 1,166 ms, the same APK installed again 1,138 ms, main with the 0.5.0
  reader files 1,206 ms. The 0.6.0 reader costs nothing measurable; the +54
  to +81 ms in earlier budgets runs is base-against-branch spread, which
  read 233 and 224 ms (#25) and 406 and 303 ms (#24) for the same app code.
- v0.2.0 is tagged at b4f61a3 and released with its APK, bundle, debug
  APK, empty app, phone test files and a `.sha256` for each (made from the
  maintainer's computer).
- 0.3.0 plan (PR #30), below.
- Reader files and core from desktop v0.8.0 (PR #31, 0b13685), with
  `scripts/update-reader-assets.sh`; Mermaid and the fonts are unchanged.
  A document without an outline now gets its text column from 1180 px
  wide: on the tablet emulator (pixel_tablet, API 34) in landscape the page
  is 1280 px and the column went from 220 to 720 px. `WideLayoutTest` runs
  on the phones and in a new tablet emulator job. A theme change redraws
  diagrams only when their colours change: the theme switch on large.md
  went from 9,849 to 5,271 ms and from 8,541 to 4,112 ms (run 44313165483,
  two attempts). The Language group of the settings panel stays hidden
  until the app reports a language.
- Simplified Chinese interface (PR #32), from desktop 0.8.0. The page
  opens as `reader.html?lang=en` or `?lang=zh-CN`, so its first paint is
  in the interface language; the host message names the setting and the
  phone's language, which shows the Language group (System, English,
  简体中文) in the sheet. A choice is saved as `language`; settings from
  0.2.0 have none and follow the phone. System follows the language
  Android picks for the app (zh-CN, zh-SG, zh-Hans and zh give Chinese;
  Traditional Chinese gives English), including a per-app language on
  Android 13. The app's own text moved to string resources:
  `values-zh-rCN` has the desktop's Chinese for the three strings the
  desktop translates (`cannot_parse`, `too_large`,
  `open_before_copying`). These have no desktop translation and stay
  English: `cannot_read`, `too_large_for_device`, `no_file_picker`,
  `no_app_for_link`, `changed_copy_again`, `section_too_large`,
  `link_scheme_blocked`, `link_other_files`, `shared_text`, `untitled`,
  `webview_needed`, `webview_none`. The strings android-host.js adds use
  the desktop's keys. The bundle keeps every language in the base APK, so
  a phone set to English can show the Chinese strings; CI checks it. APK
  2,258,929 to 2,261,401 bytes. Tests: `LanguageTest` (11),
  `InterfaceLanguageTest` (11).
- A theme the page chooses is saved and no longer sent back (PR #34), as
  desktop 0.7.0 did on Linux. With the 0.2.0 reader the answer drew every
  diagram again; the v0.8.0 reader already skips that redraw, so the theme
  switch on large.md stays the same: 4,181 against 4,145 ms (run
  44318726659).
- Version 0.3.0 (versionCode 5): `CHANGELOG.md` (new, back to 0.1.0),
  notes in `docs/releases/v0.3.0.md`,
  `fastlane/metadata/android/en-US/changelogs/5.txt`, and two README
  lines on the interface languages and the changelog. Against v0.2.0
  (draft #36, run 44330585001, two attempts): APK 2,251,229 to 2,261,389
  bytes; theme switch on large.md 5,804 and 8,165 to 3,158 and 4,388 ms;
  simple.md drawn less the empty app 172 and 432 to 156 and 412 ms; total
  PSS after large.md 389,605 and 397,726 to 393,418 and 399,873 KB.

## In progress

- The v0.3.0 tag, the release and its files. This session cannot create
  tags, so they are made from the maintainer's computer: see "Releasing
  0.3.0" below.

## Releasing 0.3.0

After the version bump is merged, from a clone of main at that commit:

```sh
git tag -a v0.3.0 -F docs/releases/v0.3.0.md
git push origin v0.3.0
gh release create v0.3.0 --draft --title "LeanMark 0.3.0" --notes-file docs/releases/v0.3.0.md
gh workflow run release.yml -f tag=v0.3.0
# When the run has attested and attached the files:
gh release edit v0.3.0 --draft=false
gh workflow run play-bundle.yml -f tag=v0.3.0
```

The "Release files" run attests `LeanMark-0.3.0.apk` and
`LeanMark-0.3.0.aab`, then attaches them with `LeanMark-0.3.0-debug.apk`,
`empty-app.apk`, `LeanMark-0.3.0-phone-test-files.zip` and a `.sha256` for
each. Check a file with
`gh attestation verify LeanMark-0.3.0.apk --repo abooodbah/leanmark-android`.
The "Play bundle" run keeps `LeanMark-0.3.0-play.aab` and its `.sha256` as
a workflow artifact for 7 days; nothing is uploaded to Google Play.

## Plan for 0.3.0

Desktop LeanMark released 0.7.0, 0.7.1 and 0.8.0 after Android 0.2.0
(2026-11-06), and added build provenance attestations on 2026-11-18.
Android 0.2.0 has the reader files from desktop commit 1e9f20e, which is in
0.7.0 but before its last two reader changes. Sources: the desktop
`CHANGELOG.md`, `docs/releases/v0.7.0.md`, `v0.7.1.md`, `v0.8.0.md` and the
commits from 1e9f20e to v0.8.0 (0b13685).

### Desktop changes since 0.2.0

| Desktop change | Desktop version | On Android |
| --- | --- | --- |
| Current section marked in "On this page" | 0.7.0 | Already in 0.2.0 (PR #25). |
| A resize into or out of the narrow layout lays the page out once | 0.7.0 | Already in 0.2.0 (reader files from 1e9f20e). |
| Repeated headings get their ids in linear time | 0.7.0 (#30) | Port, with the reader files (step 2). |
| A theme change redraws diagrams only when their colours change | 0.7.0 (#32) | Port, with the reader files (step 2). It is the "For the desktop" row of the efficiency review. |
| Linux stops sending each theme change back to the page | 0.7.0 (#31) | Port (step 3). The Android app sends a chosen theme back too. It will save the theme and not answer. |
| Launch time workflow also measures memory with an 8 MiB file | 0.7.0 | Skip. Windows tooling; the budgets job measures the Android builds. |
| A document without an outline gets the full column from 1180 px wide | 0.7.1 (#35) | Port, with the reader files (step 2). It shows on tablets in landscape and other screens 1180 px wide or more. Checked on a tablet emulator in landscape. |
| Simplified Chinese interface and a Language choice: System, English, 简体中文 | 0.8.0 (#40) | Port (step 4). See below. |
| Chinese file dialog and start-up error boxes on Windows | 0.8.0 (#40) | Skip. Android's file picker is the system's and follows the phone's language. |
| A README section in 简体中文 | 0.8.0 (#40) | Skip. It would need new Chinese text about the Android app, and only desktop wording is used. |
| The Microsoft Store lists the app as LeanMark Markdown Viewer | 0.8.0 (#41) | Skip. The Android app name, package id and launcher label stay LeanMark. The maintainer changes the Google Play listing. |
| Website and README pages (#38, #39) | after 0.7.1 | Skip. Desktop website. |
| Build provenance attestations for release files | after 0.8.0 (#46) | Port (step 5). |

### Simplified Chinese on Android

- The reader page: the desktop's English and Chinese tables in reader.js,
  unchanged. The app opens `reader.html?lang=en` or `?lang=zh-CN`, so the
  first paint is already in the right language, and the host message names
  the saved setting and the system's language, which shows the Language
  group in the settings sheet.
- The strings android-host.js adds (the settings button's name, the "On
  this page" button, sheet title and close button) use the desktop's keys
  (`settings.title`, `outline.title`, `close.label`), so the page translates
  them with the rest.
- The app's own text (status and error messages, the name of shared text):
  Android string resources. `values/strings.xml` is English;
  `values-zh-rCN/strings.xml` has only the strings whose English the desktop
  translates, with the desktop's Chinese. Android uses zh-rCN for zh-CN,
  zh-SG, zh-Hans and a bare zh, and English for Traditional Chinese
  (zh-TW, zh-HK, zh-MO, zh-Hant), as the desktop does. A JVM test checks
  each Chinese string against the desktop tables, and lists the English
  strings with no desktop translation.
- Messages from the C++ core (for example "This file is not valid UTF-8")
  are the desktop's English sentences; the page translates them as on the
  desktop.
- The setting: System, English or 简体中文, saved as `language` beside the
  theme. A missing or unknown value gives System, so settings from 0.2.0
  follow the phone and an English phone sees no change. System follows the
  language Android picks for the app, including a per-app language on
  Android 13 and later.

### Steps

Each step is its own pull request: failing tests first, then the change,
merged when CI is green. This file is updated with every merge.

1. This plan.
2. Reader files and core from desktop tag v0.8.0 with
   `scripts/update-reader-assets.sh`. The desktop pins the same Mermaid
   (11.16.1) and fonts (5.3.0), so those files stay. Tests first: a document
   without an outline gets the Narrow, Wide and Full columns, centred, on a
   page 1300 px wide, and on a tablet emulator in landscape. CI gets a
   tablet emulator job (`pixel_tablet`, API 34) for the wide-screen tests.
   The budgets job gives theme switch, open time and memory against main.
3. Stop sending a chosen theme back to the page, with the theme switch
   timed before and after.
4. Simplified Chinese interface, as above, with emulator tests: open through
   `ACTION_VIEW` with a `content://` URI in each language, the sheet's
   Language choice saved and used on the next start, rotation, a corrupt
   file and a file over the size limit with their messages in Chinese, and
   0.2.0 settings on an English phone.
5. Build provenance attestations: `release.yml` gives its job `id-token:
   write` and `attestations: write`, and runs
   `actions/attest-build-provenance` v4.2.2 (pinned to
   4d101475d8b20a2381f78447822ac1eab6504dd8) on the APK and the bundle
   after they are built, checked and hashed, before they are attached to
   the release. The README says how to check a download with
   `gh attestation verify` and that this is not code signing.
6. Version 0.3.0 (versionCode 5): `CHANGELOG.md`, README, the F-Droid
   changelog `5.txt` and `docs/releases/v0.3.0.md`. This session cannot
   push tags, so the tag, the release and the "Release files" and "Play
   bundle" runs are left for the maintainer.

Every change keeps the stack (Kotlin, classic Views, WebView), adds no
library to the app, and keeps the app without permissions.

## Desktop features the app lacks

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
| Theme switch (`reader.js`) | Every Mermaid diagram is drawn again in the new colours, and again when the app sends the theme back | 5,228 ms on large.md (11 diagrams) | Desktop 0.7.0 skips the redraw when the diagram colours stay the same | Fixed with the v0.8.0 reader files in #31: 9,849 to 5,271 ms and 8,541 to 4,112 ms (run 44313165483). #34 stops the answer |
| Zoom (`WebSettings.textZoom`) | Enlarges the toolbar too and lays the whole page out again | 786 ms per step on large.md | The reader zooms only the document (desktop 0.6.0) | Replaced in #19: 1,405 to 1,144 ms per step (run 41987196578) |
| Dark mode switch (manifest `configChanges`) | Android recreates the activity: the file is read, parsed and drawn again | 1,245 ms to drawn on large.md, against 167 ms for a rotation | Handle `uiMode` in the activity | Fixed in #28: first frame 1,369 to 1,198 ms, diagrams 4,550 to 3,228 ms, position kept |
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

- The tablet emulator job runs only `WideLayoutTest`; the other tests
  assume a phone. On that tablet Android 14 letterboxes the app when it
  asks for portrait, so the test's portrait page is 463 px wide.
- The Chinese text has not been reviewed by a native speaker, as on the
  desktop. Traditional Chinese keeps English. The twelve app strings
  listed under Done stay English in a Chinese interface, and text inside
  Mermaid diagrams, the store texts, the fastlane texts and the site are
  English.
- The per-app language test needs Android 13, so it runs on API 34 only.

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

0.3.0 against v0.2.0, from the budgets job on an API 34 emulator
(SwiftShader, no GPU), release builds measured in turns, draft #36, run
44330585001, attempts 1 and 2. Sizes from the build job of main at
be4e78d (run 44336902352), the same code as 0.3.0 but the version.

| Measure | 0.2.0 | 0.3.0 |
| --- | ---: | ---: |
| Release APK, all ABIs (bytes) | 2,251,229 | 2,261,389 |
| Release bundle (bytes) | 2,062,283 (0.2.0 release PR) | 2,075,505 |
| What Play delivers to an arm64-v8a phone (bytes) | 1,612,006 installed, 1,294,807 download (0.2.0 release PR) | 1,625,706 installed, 1,305,743 download |
| simple.md drawn minus the empty app's first frame (ms) | 172, 432 | 156, 412 |
| large.md drawn after a cold start (ms) | 1,499, 2,715 | 1,736, 2,655 |
| Total PSS after large.md, app and renderer (KB) | 389,605, 397,726 | 393,418, 399,873 |
| WebView renderer PSS after large.md (KB) | 314,865, 322,732 | 315,960, 322,879 |
| Janky frames over 10 swipes (software GPU) | 79.29%, 87.03% | 87.80%, 87.85% |
| Theme switch on large.md, key to drawn (ms) | 5,804, 8,165 | 3,158, 4,388 |
| Rotation on large.md (ms) | 153, 184 | 160, 215 |

Start time, open time, rotation and jank move by more than these
differences between runs of the same build on this emulator; see the PR
bodies of #31, #32 and #34 for further samples. The phone budget needs a
real phone.

Tests on main (be4e78d): 44 JVM tests, the desktop core's test program (9
groups), 91 instrumented tests on API 34 (all pass), 91 on API 26 (83
pass, 8 skipped because they need WebView 94 or Android 13), and 4 `WideLayoutTest` tests on the tablet emulator.

## Phone test

The emulator in CI has no GPU, so its start times and jank do not show what
a phone does. This is the check to run on a real phone. It takes about ten
minutes.

### Install

From the release page on GitHub, download onto the phone:

- `LeanMark-0.3.0.apk`: the release build (minified), signed with the
  release key. Use this one for timing.
- `LeanMark-0.3.0-debug.apk`: the debug build, for comparison only. It is
  slower by design.
- `empty-app.apk`: an app with one empty screen, for the start-time
  baseline with adb (it has no launcher icon).
- `LeanMark-0.3.0-phone-test-files.zip`: the files below. Unzip it into
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
