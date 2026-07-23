# Known Limitations

## Security and backend

- `demoPayout` is unauthenticated, state-changing, demo-specific, and accepts unrestricted HTTP methods.
- Cashfree beneficiary details and demo identifiers are hard-coded.
- Razorpay contact/fund-account APIs are called from Android client code; sensitive provider operations belong on a trusted backend.
- Firebase rules and Firestore index files referenced by `firebase.json` are missing.
- Several flows fall back from encrypted to plain `SharedPreferences`.
- The manifest currently permits cleartext traffic.

## Deployment

- Only `payouts/` is configured as a Firebase Functions codebase.
- Root `functions/index.js` notification triggers are not deployable with the checked-in configuration.
- `.firebaserc`, `google-services.json`, and one hard-coded RTDB URL reference different Firebase project identifiers; environments need consolidation.
- No verified public Android release or API endpoint is published.

## Quality

- Automated coverage is limited to the generated example unit test.
- No instrumentation, integration, Firebase emulator, or backend tests are checked in.
- Android lint has known blocking errors and many warnings.
- Several Activities are very large and access Firebase directly.
- Both `qrHistory` and `qr_history` naming variants occur.
- Currency symbols appear encoding-corrupted in some Java strings.

## Product

- Privacy-policy and terms links point to `example.com`.
- Runtime screenshots and a demo GIF have not been captured.
- There is no license file.
- Accessibility, localization, offline-conflict behavior, and production recovery procedures are not documented or fully tested.
