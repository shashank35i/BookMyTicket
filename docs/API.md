# API Documentation

## HTTP function: `demoPayout`

Source: `payouts/index.js`  
Runtime: Node.js 22  
Region: `asia-south1`  
Method implemented: `GET` or `POST` are both accepted by the current `onRequest` handler.

The deployed URL normally follows:

```text
https://asia-south1-{firebase-project-id}.cloudfunctions.net/demoPayout
```

No live endpoint is asserted here because deployment has not been verified.

### Behavior

The handler:

1. Queries up to three Firestore `payouts` documents for a hard-coded demo user.
2. Adds or reuses a hard-coded Cashfree beneficiary.
3. requests an IMPS transfer.
4. writes `payout_logs`.
5. generates a PDF report.
6. sends the report through Gmail/Nodemailer.

### Responses

| Status | Meaning |
| --- | --- |
| `200` | Payout processed and email sent |
| `400` | No eligible tickets or total payout is zero |
| `500` | Provider, Firestore, PDF, or email operation failed |

The current response body is plain text. There is no versioned JSON response contract.

### Security warning

The handler currently has no explicit authentication, authorization, method restriction, idempotency key, request schema, or rate limiting. It is state-changing even when invoked by `GET`. Do not expose or load-test it in its current form.

Required Firebase runtime configuration keys:

```text
cashfree.client_id
cashfree.client_secret
cashfree.public_key
email.user
email.pass
```

Never commit their values.

## Event-driven notification functions

`functions/index.js` exports:

| Function | Trigger |
| --- | --- |
| `sendTouristNotification` | Write to `/tourist_notifications/{userId}/{notificationId}` |
| `sendPlaceAdminNotification` | Write to `/placeadmin_notifications/{userId}/{notificationId}` |

Both look up `users/{userId}/fcmToken`, validate it with a dry-run message, send FCM, and remove invalid/stale tokens. These triggers are not wired into the current `firebase.json`.

## Third-party APIs called by Android

The Android client calls Razorpay `/v1/contacts` and `/v1/fund_accounts` from bank/setup screens and uses Razorpay Checkout. Direct provider API access from a mobile client is not an acceptable production trust boundary; proxy these operations through an authenticated backend.
