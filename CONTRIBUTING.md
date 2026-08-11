# Contributing to Habitly

Thanks for taking the time to contribute.

## Local setup

1. Install JDK 21, Android Studio and the Android SDK declared by the project.
2. Copy `app/google-services.json.example` to `app/google-services.json`, or provide a Firebase
   configuration for your own development project.
3. Run the Android checks:

   ```shell
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

4. Install Node.js 22 and validate Firestore rules:

   ```shell
   npm ci
   npm run test:firestore-rules
   ```

The rules suite uses a Firebase demo project and the local emulator. It cannot access production
services.

Release builds and Play Console publication follow the checklist in
[`docs/releasing.md`](docs/releasing.md).

## Pull requests

- Keep changes focused and explain user-visible behavior and security implications.
- Add tests for fixes and new behavior.
- Never commit signing files, Firebase production configuration, tokens or real user data.
- Use fictional households, names and invite codes in fixtures and screenshots.
- Ensure all CI checks pass before requesting review.

By contributing code, you agree that it is licensed under GPL-3.0 as described in `LICENSE`.
