# LeanMark for Android

A read-only viewer for Markdown files on Android. It is the Android companion
to the desktop app at https://github.com/abooodbah/leanmark and shows
documents with the same reader page.

The interface is in English or Simplified Chinese. It follows the phone's
language, or the Language choice in the settings sheet.

Work in progress. Not yet on Google Play. The changes in each version are
in [CHANGELOG.md](CHANGELOG.md).

## Try it (closed test)

LeanMark for Android is in a closed test on Google Play before its public release.
To try it, follow the steps below or on the [LeanMark for Android site](https://abooodbah.github.io/leanmark-android/):

1. Join the [Lean Apps testers group](https://groups.google.com/g/lean-apps-testers) with the Google account you use
   on your phone.
2. On your phone, open the [LeanMark tester page](https://play.google.com/apps/testing/io.github.abooodbah.leanmark), tap
   **Become a tester**, then install LeanMark from Google Play.

If the tester page says the app is not available yet, Google is still reviewing
the test; try again a day later. Google asks testers to keep the app installed
for 14 days. The APK is also attached to each
[Android release](https://github.com/abooodbah/leanmark-android/releases).

## Checking a download

Each file on a release page has a `.sha256` next to it. From 0.3.0 the APK
and the bundle also carry a build provenance attestation: a record, signed
through GitHub, that the file was built by this repository's "Release
files" workflow from the tagged source. To check one with the GitHub CLI:

```sh
gh attestation verify LeanMark-0.3.0.apk --repo abooodbah/leanmark-android
```

This shows where a file was built. It is not code signing: Android checks
the APK's own signature, made with the same release key as before.

## Building

You need JDK 17 and the Android SDK (platform 36). Then:

```sh
./gradlew lint testDebugUnitTest assembleRelease bundleRelease
```

Instrumented tests need a running emulator or a connected device:

```sh
./gradlew connectedDebugAndroidTest
```

CI runs all of these on every pull request, with the instrumented tests on
API 26 and API 34 emulators.

## Privacy

The app requests no permissions, including no network permission. Files are
read with the access the opening app grants and are never changed.

## Signing for Google Play

Release APKs attached to GitHub releases are signed with the maintainer's
release key, which stays the same from version to version. A local release
build without a key comes out unsigned. Google Play needs a bundle signed with
your own upload key. The
key stays on your computer: never commit it, and never put its password in
this repository. `.gitignore` already excludes `*.jks`, `*.keystore` and
`keystore.properties`.

1. Create the upload key once. `keytool` comes with the JDK:

   ```sh
   keytool -genkeypair -v -keystore ~/keys/leanmark-upload.jks \
     -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```

   Keep a backup of the file and its password somewhere safe. If you lose it,
   Google can reset the upload key, but that takes a support request.

2. Tell Gradle where it is. Create `keystore.properties` in the repository
   root (it is ignored by git):

   ```properties
   storeFile=/home/you/keys/leanmark-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```

3. Build the signed bundle:

   ```sh
   ./gradlew :app:bundleRelease
   ```

   The file is `app/build/outputs/bundle/release/app-release.aab`.

4. In Play Console, create the app, accept Play App Signing, and upload that
   bundle. Google keeps the app signing key; your key only proves uploads come
   from you.

The bundle attached to a GitHub release is signed with CI's throwaway key,
so build the one for Play yourself as above.

## Store listing

`store/` holds what Play Console asks for: the short and full
descriptions, the data safety and content rating answers, the closed-test
plan (`store/closed-test.md`), and light and dark screenshots taken on an
emulator by the `screenshots` CI job (`scripts/screenshots.sh`).
