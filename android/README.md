# ClipVlip Android

## Update architecture

ClipVlip supports two mutually compatible native update routes:

- **Direct release:** signed APKs published as GitHub Releases assets. The app checks the latest release, requires a SHA-256 digest, downloads and verifies the APK, then hands installation to Android. Android requires the user to approve installation and allow this app as an install source.
- **Google Play:** Google Play In-App Updates SDK is included. This route works only when the installed package was installed through Google Play and a compatible update is available to that Play account/device. Flexible update checks are supported by the Play SDK.

The app stores an update-channel preference (automatic/direct/Google Play). Android/Play installation source restrictions still apply; selecting Play does not turn a sideloaded install into a Play install.

## Release version control

- Work on feature branches and merge reviewed PRs into `main`.
- Tag releases using semantic versions, for example `v0.2.0`.
- Tag push runs `.github/workflows/release-android.yml`, building a signed APK and AAB and attaching them to the GitHub Release.
- Build versionCode is generated from the release workflow run number. Do not reuse a versionCode.
- The same Android upload/signing key must be retained for every update to an existing installation.

## Required GitHub Actions secrets

Before the first signed release, configure:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded JKS/PKCS12 upload keystore.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Do not commit the keystore or passwords. Losing the signing key prevents direct APK updates from installing over an existing copy. Keep a secure offline backup.

Google Play publishing is optional and disabled until repository variable `GOOGLE_PLAY_ENABLED=true` and secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` are configured. The workflow can then be manually dispatched to upload an AAB to internal, alpha, beta or production tracks. A Play Console app, Play App Signing and service-account permissions are prerequisites.

## Server-driven configuration

`GET /api/mobile/config` returns public feature flags and minimum supported app version. Environment variables:

- `CLIPVLIP_MIN_APP_VERSION`
- `CLIPVLIP_MAINTENANCE_MODE`
- `CLIPVLIP_MAINTENANCE_MESSAGE`
- `CLIPVLIP_DIRECT_UPDATES`
- `CLIPVLIP_PLAY_UPDATES`

Never put Supabase service keys, signing credentials, or service-account JSON in this endpoint or the APK.

## Important limitations

Native Kotlin, permissions, SDKs and device integrations require a new signed APK/AAB. Server-driven settings/content can change without reinstalling. Direct APK installation is user-approved by Android, never silent. Google Play in-app updates require Play distribution.
