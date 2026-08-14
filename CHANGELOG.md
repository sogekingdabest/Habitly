# Changelog

All notable user-facing changes to Habitly are documented here. Versions follow
[Semantic Versioning](https://semver.org/).

## [1.0.4] - 2026-08-14

### Changed

- Maintenance release updating Firebase, Google Sign-In, Hilt, and the Android build toolchain.
- Upgraded to Android Gradle Plugin 9.3.1 and Gradle 9.5.0.

### Security

- Expanded CodeQL analysis across the Android app and repository tooling.

## [1.0.3] - 2026-08-11

### Added

- Personal and household notes with search, pinning, and an improved editor.
- More flexible routine scheduling, templates, reminders, and household progress views.
- Play Integrity-backed Firebase App Check for release builds.
- Automated Firestore Security Rules tests and mandatory Android CI checks.

### Changed

- Refined navigation, layouts, empty states, dialogs, and landscape behavior across the app.
- Improved shopping, pantry, routine, and dashboard workflows.
- Hardened household membership, ownership, invite expiry, and invite rotation rules.

### Fixed

- Corrected permission failures affecting personal notes and household membership flows.
- Fixed release-only issues involving R8, Room, LiteRT-LM, widgets, and model loading.
- Resolved the lint backlog and a collection of device-tested usability issues.

[1.0.4]: https://github.com/sogekingdabest/Habitly/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/sogekingdabest/Habitly/commits/v1.0.3
