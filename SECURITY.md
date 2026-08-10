# Security Policy

## Supported versions

Security fixes are applied to the latest version on `main` and to the current production release.
Older versions may be asked to update before receiving a fix.

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability or include user data, credentials,
household identifiers, invite codes or exploit details in public discussions.

Report vulnerabilities through a
[private GitHub security advisory](https://github.com/sogekingdabest/Habitly/security/advisories/new).
Include the affected version, reproduction steps, expected impact and any suggested mitigation.

You should receive an acknowledgement within seven days. A validated report will be handled before
technical details are disclosed publicly.

## Backend configuration

The Firebase API key used by the Android client is not an authorization secret. Access is enforced
by Firebase Authentication, Firestore Security Rules and App Check. Production deployments must
follow [`docs/production-security-checklist.md`](docs/production-security-checklist.md).
