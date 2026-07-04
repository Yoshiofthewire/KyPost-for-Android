# llama mail for Android

This app now includes a production push client flow for **Llama Labels notifications via Novu + FCM**.

## What this build does

- Pairs device from desktop deep link / QR:
  `llamalabels://novu-pair?app=<applicationIdentifier>&sub=<subscriberId>&hash=<subscriberHash>&api=<novuApiBase>`
- Stores pairing state in DataStore (no backend secrets).
- Registers FCM token directly with Novu on pair + token refresh.
- Handles FCM data payload keys: `messageId`, `senderName`, `emailSubject`, `Keywords`.
- Shows system notifications and keeps an in-app notification history.
- Does not call Llama Labels backend endpoints from mobile runtime.

## Firebase setup

1. Create/update your Firebase Android app for application id `com.urlxl.mail`.
2. Download `google-services.json`.
3. Place it at `app/google-services.json`.
4. Ensure FCM is enabled in Firebase project settings.

## Notification permission behavior (Android 13+)

- App requests `POST_NOTIFICATIONS` at launch.
- If denied, push payloads are still parsed and saved to in-app history when delivered to app process, but system notifications are not shown.

## Pairing from desktop QR

1. Desktop web shows QR containing deep link with `app`, `sub`, `hash`, optional `api`.
2. In app Home screen, either:
   - Tap **Scan QR**, or
   - Paste deep link into **Pair from link** field.
3. App validates required params and stores pairing.
4. App immediately tries Novu token sync with retry backoff.

## Troubleshooting checklist

- Verify deep link scheme/host is exactly `llamalabels://novu-pair`.
- Verify required query params exist: `app`, `sub`, `hash`.
- Verify network access to Novu API base (`api` param or default `https://api.novu.co`).
- Verify Firebase project config matches package `com.urlxl.mail`.
- If token sync fails with 401/403/404, desktop-side pairing may be revoked.
- If no visible notification on Android 13+, verify notification permission is granted.

## Build and test

```sh
./gradlew testDebugUnitTest
```

```sh
./gradlew assembleDebug
```

## Test coverage included

- Deep-link parser tests
- Pairing validation tests
- Payload parser tests (`messageId`, `senderName`, `emailSubject`, `Keywords`)
- Novu registration request mapper tests

