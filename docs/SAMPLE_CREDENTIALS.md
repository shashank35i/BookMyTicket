# Sample Credentials

BookMyTicket uses Firebase phone OTP authentication. There is no username/password login and no real shared credential should be committed.

Configure fictional Firebase Authentication test phone numbers in your own non-production Firebase project:

| Role | Example test phone | Example fixed OTP | Purpose |
| --- | --- | --- | --- |
| Tourist | `+1 650-555-0101` | `111111` | Tourist onboarding and ticket flow |
| Place admin | `+1 650-555-0102` | `222222` | Place setup and QR validation |
| Parking admin | `+1 650-555-0103` | `333333` | Parking setup and vehicle scanning |

These are documentation examples only. They are not configured in the repository's Firebase project and will not work until added under Firebase Authentication → Sign-in method → Phone numbers for testing.

Use fictional place, vehicle, bank, and email data. Use Razorpay/Cashfree sandbox accounts. Never add real OTPs, API secrets, bank details, service-account JSON, or signing keystores to Git.
