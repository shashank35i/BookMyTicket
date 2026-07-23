# Repository Guidelines

## Project Structure & Module Organization

This repository contains one native Android application module, `app`, using Java 8, XML layouts, and base package `com.example.bookmyticket`. Role-owned screens are grouped under `roles/tourist`, `roles/placeadmin`, and `roles/parkingadmin`. Phone OTP login, first-user role selection, account routing, and session creation live under `auth`; SMS signature support lives under `security`. Startup classes are in `core`, Firebase/FCM services in `notification`, and data objects in `model`.

Cross-role capabilities use feature packages: QR scanning in `features/scanner`, ticket history/details in `features/tickets`, and shared payment, reporting, and settings screens in their matching `features/*` package. Custom reusable views live in `ui`; their fully qualified names are referenced by XML layouts. Resources remain under `app/src/main/res`. Firebase Authentication and Realtime Database connect the features through nodes including `users`, `tickets`, `payouts`, `vehicle_details`, and role-specific notifications.

`payouts/` is the Node 22 Firebase Functions codebase selected by `firebase.json`; it handles Cashfree payout, PDF, and email work. Root-level `functions/index.js` contains Realtime Database notification triggers but is **not** listed as a deployable source in the current Firebase configuration. Confirm deployment intent before changing either backend. Treat `app/google-services.json`, `local.properties`, and Firebase function configuration as environment-sensitive.

## Build, Test, and Development Commands

Run commands from the repository root on Windows:

- `.\gradlew.bat app:assembleDebug` builds the debug APK.
- `.\gradlew.bat app:installDebug` installs it on a connected device or emulator.
- `.\gradlew.bat app:testDebugUnitTest` runs local JUnit 4 tests.
- `.\gradlew.bat app:testDebugUnitTest --tests "com.example.bookmyticket.ExampleUnitTest.addition_isCorrect"` runs one test method.
- `.\gradlew.bat app:connectedDebugAndroidTest` runs device tests.
- `.\gradlew.bat app:lintDebug` runs Android lint.
- `cd payouts; npm run lint` checks Cloud Functions JavaScript.
- `cd payouts; npm run serve` starts the Functions emulator; `npm run deploy` deploys the configured functions codebase.

## Coding Style & Naming Conventions

Follow the existing Android style: four-space indentation, PascalCase classes, camelCase members/methods, `*Activity` screen names, explicit imports, and snake_case resource names such as `activity_ticket_details.xml`. Put a screen in `roles.*` only when exclusive to that role; use `features.*` for cross-role workflows. Keep Firebase DTOs in `model` and compatible with Firebase deserialization. JavaScript in `payouts/` is enforced by ESLint’s recommended and Google configs, ECMAScript 2018, double quotes, and arrow callbacks.

## Testing Guidelines

Local tests belong in `app/src/test/java`; instrumentation tests belong in `app/src/androidTest/java`. The repository currently contains only the generated `ExampleUnitTest`, with no declared coverage threshold or backend test script.
