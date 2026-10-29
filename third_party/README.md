# Third-party and shared files

These files are copied, not written here. `scripts/update-reader-assets.sh`
copies them from a checkout of the desktop app and records their SHA-256 sums
in `reader-assets.sha256`. A unit test fails if a copied file changes without
the sums file changing with it.

| Files | From | Version |
| --- | --- | --- |
| `app/src/main/assets/reader/reader.js`, `reader.css` | desktop LeanMark `assets/` | commit in `reader-assets.sha256` |
| `app/src/main/assets/reader/reader.html` | desktop LeanMark `assets/reader.html`, plus one `<script>` line for `android-host.js` | same |
| `app/src/main/cpp/core/` | desktop LeanMark `src/core/` | same |
| `native-tests/CoreTests.cpp` | desktop LeanMark `tests/linux/CoreTests.cpp` | same |
| `app/src/main/cpp/md4c/` | MD4C, through the desktop's `third_party/md4c/` | 0.5.3 |
| `app/src/main/assets/reader/vendor/mermaid.min.js` | npm `mermaid` | 11.16.1 |
| `app/src/main/assets/reader/fonts/` | npm `@fontsource/ibm-plex-sans`, `-serif`, `-mono` | 5.3.0 |

`app/src/main/assets/reader/android-host.js` is the only reader file written
for Android. Licenses are in `app/src/main/assets/THIRD_PARTY_NOTICES.md`,
which ships inside the app.
