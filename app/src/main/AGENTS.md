# Purpose

Owns production Android app code and resources.

# Ownership

- Code: `app/src/main/java/com/urlxl/mail/`
- Resources: `app/src/main/res/`
- Manifest: `app/src/main/AndroidManifest.xml`

# Local Contracts

- Launcher supports `llamalabels://novu-pair` deep links and QR/manual pairing for Novu push onboarding.
- Pairing data is persisted via DataStore: application identifier, subscriber id/hash, api base, and paired-at timestamp.
- Mobile registers FCM tokens directly with Novu and does not call Llama Labels backend APIs.
- Incoming FCM payload parser contract keys are exact: `messageId`, `senderName`, `emailSubject`, `Keywords`.
- Push notifications are shown via Android notification channel and copied into in-app history preview.
- Android 13+ notification runtime permission is requested from launcher UI.
- Launcher is a push-focused home screen in `MainActivity` that manages pairing, token sync actions, and push history preview.
- Mail config (IMAP/SMTP host, port, credentials) is still persisted in `SharedPreferences` and entered via `SettingsActivity` for legacy mail screens.
- Required fields for mail config: IMAP host, SMTP host, username, password. Ports default to 993 (IMAP) and 587 (SMTP). IMAP folder defaults to "INBOX".
- Inbox tabs are derived from IMAP user flags (keywords) attached to messages.
- Keyword tuning is managed in `KeywordSettingsActivity` and persists hidden/visible keyword headings.
- Theme selection is managed in `ThemesActivity` and uses the shared theme name list based on `theme.ts` palettes.
- Keyword refresh is best-effort every 90 seconds while inbox UI is foregrounded.
- Background keyword staleness is accepted; app catches up on next foreground refresh.
- Use existing lightweight mail transport dependency for both IMAP and SMTP.

# Work Guidance

- Keep network off the main thread.
- Keep lifecycle-safe polling: start in foreground lifecycle, stop on background lifecycle.
- Prefer immutable model updates for inbox list and keyword tabs.
- Do not add new dependencies unless they reduce overall code size/complexity.

# Verification

- Add or update unit tests in `app/src/test/` for tab computation and filtering logic.
- Add or update unit tests for deep-link parsing, pairing validation, payload parsing, and Novu request mapping.
- Validate manifest registration when adding activities or permissions.

# Child DOX Index

- No child AGENTS.md files.
