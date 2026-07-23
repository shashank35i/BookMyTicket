# Load-test Results

## Current evidence

No remote load test has been executed. The only checked-in HTTP function, `demoPayout`, initiates a payout, writes Firestore state, and sends email; repeatedly invoking it would create side effects and is unsafe. Reporting invented throughput or latency would be misleading.

| Item | Status |
| --- | --- |
| Android debug assembly | Passed locally |
| Android unit-test task | Passed locally |
| Safe health/read endpoint | Not implemented |
| Remote concurrency test | Not run |
| Throughput / p95 / error rate | No valid measurement yet |

## Prepared test asset

`load-tests/payout-smoke.js` is deliberately guarded. It will not execute unless both a target URL and an explicit destructive-test acknowledgement are supplied. Use only against an isolated Firebase project containing synthetic data and sandbox providers.

```powershell
$env:BASE_URL='https://example.invalid'
$env:ALLOW_DESTRUCTIVE_PAYOUT_TEST='true'
k6 run load-tests/payout-smoke.js
```

## Acceptance criteria for a publishable run

Record the commit SHA, Firebase region/runtime, instance limits, dataset size, provider sandbox, virtual-user profile, request count, p50/p95/p99 latency, error rate, and any side effects. A safer prerequisite is to add an authenticated, read-only health endpoint and load-test that endpoint first.
