# Production security checklist

Complete this checklist for every Firebase production project. Repository CI cannot inspect or
change these console settings.

## Firestore

- Run `npm run test:firestore-rules` locally or confirm the `Firestore rules` CI job is green.
- Deploy the reviewed rules with `firebase deploy --only firestore:rules --project <project-id>`.
- Confirm the deployed rules timestamp and project id in Firebase Console.
- Verify that an expelled test account cannot rejoin with a known household id or an expired code.

## App Check

- Register the production Android app with Play Integrity.
- Register every active Play app-signing certificate in Firebase.
- Monitor verified and unverified traffic before enforcement when existing versions are installed.
- Enforce App Check for Cloud Firestore and Authentication when legitimate production traffic is
  verified.
- Register debug tokens only in development projects; never commit or share them.

## Google Cloud and GitHub

- Restrict Firebase-provisioned API keys to the Firebase APIs used by the app.
- Enable GitHub secret scanning, dependency alerts and private vulnerability reporting.
- Require the CI workflow and a reviewed pull request before merging to `main`.
- Keep the upload keystore, `keystore.properties`, `local.properties` and the real
  `google-services.json` outside Git.

## Release evidence

Record the app version, rules deployment time, App Check enforcement status and successful CI run
in the private release checklist. Do not store production identifiers, tokens or user data in the
public repository.
