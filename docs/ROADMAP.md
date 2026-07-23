# Future Improvements

## P0 — production safety

- Require Firebase-authenticated, role-authorized calls for payout operations.
- Move all payment-provider secret operations behind backend APIs.
- Add idempotency, request validation, structured JSON errors, audit logs, and rate limiting.
- Check in least-privilege Realtime Database and Firestore rules plus indexes.
- Remove hard-coded demo beneficiary data and unify Firebase environments.

## P1 — reliability and maintainability

- Introduce repositories/use cases so Activities do not directly own Firebase operations.
- Add unit tests for ticket validity, pricing, role routing, and payout calculations.
- Add Firebase Emulator integration tests and Android instrumentation tests.
- Resolve lint blockers, encoding issues, deprecated APIs, and oversized Activities.
- Standardize `qrHistory` naming and formalize database migrations.
- Add CI for build, unit tests, lint, secret scanning, and dependency review.

## P2 — product readiness

- Publish privacy policy and terms pages.
- Improve accessibility, localization, error recovery, and offline behavior.
- Capture role-based screenshots and a short demo GIF from a seeded test environment.
- Publish signed releases with changelogs and checksums.
- Add observability dashboards, crash reporting, and alerting.

## P3 — scale evidence

- Add a read-only authenticated health endpoint.
- Define service-level objectives and representative workload models.
- Run staged load tests against emulators, then a dedicated non-production project.
- Publish latency percentiles, throughput, error rates, environment size, and commit SHA.
