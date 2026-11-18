# Changelog

All notable changes to LeanMark for Android. The project follows semantic
versioning. The release notes for each version are in `docs/releases/`
(from 0.2.0) and, shorter, in `fastlane/metadata/android/en-US/changelogs/`.

## [0.3.0] - 2026-11-18

The reader page and the Markdown core from desktop LeanMark 0.8.0.

### Added

- Simplified Chinese interface, from desktop 0.8.0. The app shows it when
  the phone's language is Simplified Chinese (zh-CN, zh-SG, zh-Hans), and
  the settings sheet has a Language choice: System, English or 简体中文,
  saved like the theme. The reader page uses the desktop's Chinese strings;
  the app's own messages use Android resources with the desktop's Chinese
  where the desktop has it, and English otherwise. Settings saved by 0.2.0
  have no language and follow the phone.
- Release files: the APK and the bundle carry a build provenance
  attestation, checked with `gh attestation verify`.

### Fixed

- A document with fewer than two second- or third-level headings, so no
  "On this page" list, was shown in a 220 px column on screens 1180 px wide
  or more, such as a tablet in landscape (desktop 0.7.1).

### Changed

- A theme change draws diagrams again only when their colours change, and
  the app no longer sends a chosen theme back to the page. On a 1 MiB
  document with 11 diagrams the theme switch takes about half as long.
- Documents with many repeated headings open faster (desktop 0.7.0).

## [0.2.0] - 2026-11-06

### Added

- A settings sheet with the theme, the text width (Narrow, Wide, Full) and
  the zoom, from desktop 0.6.0. Back closes it.
- "On this page" as a sheet on a phone, with the section you are reading
  highlighted.
- A coloured dot on the theme button.

### Changed

- Zoom and pinch enlarge only the document, not the toolbar.
- The APK is 2.3 MB instead of 4.1 MB.
- Switching the phone between light and dark keeps your place.

### Fixed

- The note for a missing image follows the theme.

## [0.1.2] - 2026-10-30

### Changed

- The APK on GitHub is signed with a stable release key, so later versions
  install as updates.

## [0.1.1] - 2026-10-29

### Fixed

- The toolbar fits a phone screen; the Open button was cut off.

## [0.1.0] - 2026-10-29

- First release: a read-only Markdown viewer with the desktop reader and
  Markdown core, Mermaid diagrams, five themes, find, section and file copy,
  and a diagram viewer. No permissions and no network.
