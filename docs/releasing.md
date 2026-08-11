# Release process

This checklist keeps the GitHub source, Firebase backend, and Play Console artifact aligned.

## 1. Prepare the version

- Create a focused release branch from the latest `main` (for example, `release/x.y.z`).
- Update `habitlyVersionName` in `app/build.gradle.kts`; `versionCode` is derived automatically.
- Update `CHANGELOG.md` and prepare localized Play Console release notes.
- Confirm the real `google-services.json`, upload keystore, and `keystore.properties` remain outside Git.

## 2. Validate

Run the same checks required by CI:

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug bundleRelease
npm ci
npm run test:firestore-rules
```

Additionally:

- Verify the release bundle is signed with the upload key.
- Install a release build or test through a Play track and smoke-test authentication, household
  membership, shopping, notes, routines, the widget, and the on-device assistant.
- Confirm the privacy policy and support links are reachable.

## 3. Backend and Play Console

- Complete `docs/production-security-checklist.md`.
- Deploy reviewed Firestore rules and record the deployment privately.
- Confirm App Check enforcement accepts the Play-distributed build.
- Upload the signed AAB to the intended Play track and verify its version name and version code.

## 4. Publish the source release

- Merge the release PR only after all required checks pass.
- Create an annotated `vX.Y.Z` tag from the merge commit.
- Create a GitHub Release from that tag using the matching changelog entry.
- Do not attach keystores, credentials, production configuration, private test evidence, or user data.

Rollback and signing details belong in the private release record, not in this public repository.
