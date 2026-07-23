# Design Decisions

## Feature-oriented Java packages

Role-exclusive screens live under `roles.*`; workflows shared by roles live under `features.*`. Infrastructure (`core`, `notification`, `security`), models, and reusable UI are separate. This keeps navigation ownership visible without duplicating scanners, tickets, reports, or settings.

## Single Android application module

The current project is small enough to build as one `app` module. Package boundaries improve discovery but do not enforce dependency direction. Separate Gradle modules may become worthwhile when automated tests and stable interfaces exist.

## Activity/XML user interface

The app uses Java Activities, View Binding, and XML resources. Reorganization preserved this implementation to avoid functional changes. A future MVVM or unidirectional-data-flow migration should be incremental and test-backed.

## Firebase as operational platform

Phone Auth, Realtime Database, and FCM minimize backend code for role-aware real-time flows. The tradeoff is that authorization and validation depend heavily on checked-in Firebase rules, which are currently missing.

## Realtime Database and Firestore coexistence

The Android app uses Realtime Database, while `demoPayout` uses Firestore. This is documented as an existing boundary, not a recommended duplication. Production design should choose a canonical payout store or implement explicit synchronization.

## No fabricated operational evidence

The repository does not claim a live deployment, demo credentials, screenshots, or load-test throughput until those artifacts are produced from a controlled environment. Documentation distinguishes source readiness from operational proof.
