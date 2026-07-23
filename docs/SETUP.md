# Setup

## Android

1. Install Android Studio, Android SDK 34, and JDK 17 (recommended for Android Gradle Plugin 8.6).
2. Clone the repository and open its root in Android Studio.
3. Supply `local.properties` through Android Studio or create it locally with your SDK path. It is ignored by Git.
4. Register the Android application ID `com.example.bookmyticket` in Firebase.
5. Put the matching Firebase Android configuration at `app/google-services.json`.
6. Enable Phone Authentication, Realtime Database, Cloud Messaging, and any required App Check provider.
7. Add SHA-1/SHA-256 fingerprints for each signing certificate used with phone auth and provider SDKs.
8. Build:

```powershell
.\gradlew.bat app:assembleDebug
.\gradlew.bat app:testDebugUnitTest
.\gradlew.bat app:installDebug
```

The app requires a device/emulator with a camera for complete scanner testing.

## Firebase Functions

Install Node.js 22 and Firebase CLI, then:

```powershell
firebase login
cd payouts
npm ci
npm run lint
npm run serve
```

Configure Cashfree and email values with your organization-approved secret-management process. The source uses legacy `functions.config()` access; verify compatibility with the deployed Firebase Functions generation before deployment.

Deploy the configured codebase from `payouts/`:

```powershell
npm run deploy
```

Root `functions/index.js` needs its own package manifest and a matching `firebase.json` codebase entry before it can be deployed.

## Test identities

Use Firebase Authentication test phone numbers; see [Sample Credentials](SAMPLE_CREDENTIALS.md). Keep test projects and provider sandbox accounts separate from production.

## Release checklist

- Add production Firebase rules and indexes.
- Move provider-secret operations to authenticated backend functions.
- Replace example privacy/terms links.
- Run Android lint and resolve blocking errors.
- Test all roles on supported API levels.
- Create a signed Android App Bundle without committing keystores.
- Publish a release URL and then update the live-deployment section in `README.md`.
