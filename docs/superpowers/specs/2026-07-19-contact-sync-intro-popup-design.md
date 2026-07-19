# Contact syncing intro popup

## Problem
Device contact syncing (`DeviceContactSyncSettings`, defaults off) is already fully
built, but users only discover it via a menu item on `ContactsListActivity`. There's
no moment where the app proactively tells users the feature exists and lets them opt
in. The pairing flow's QR scan (`PushPairingActivity`) is a natural moment to surface
it, since pairing this device is closely tied to bringing the account's data — including
contacts — onto it.

## Change

### One-time trigger
The first time the user taps "Scan QR" (`btnScanQr`) in `PushPairingActivity`, before
`scanQr()` launches the ML Kit scanner, show an intro dialog about contact syncing.
Every subsequent tap (on this device) skips straight to scanning.

A new persisted flag, `hasShownSyncIntro`, is added to `DeviceContactSyncSettings`
(same `SharedPreferences` file as `enabled` / `lastForeignScanAtEpochMs`):

```kotlin
fun hasShownSyncIntro(): Boolean = prefs.getBoolean(KEY_SHOWN_INTRO, false)
fun setHasShownSyncIntro(shown: Boolean) {
    prefs.edit().putBoolean(KEY_SHOWN_INTRO, shown).apply()
}
```

`btnScanQr`'s click listener becomes:

```kotlin
btnScanQr.setOnClickListener { onScanQrClicked() }
```

```kotlin
private fun onScanQrClicked() {
    val settings = DeviceContactsRuntime.graph(this).settings
    if (!settings.hasShownSyncIntro()) {
        showSyncIntroDialog(settings)
    } else {
        scanQr()
    }
}
```

### Dialog content and behavior
`AlertDialog.Builder`, matching the existing inline-dialog pattern (`confirmAndApplyPairing`
in this same file, and `ContactEditActivity`'s delete confirmation):

- Title: "Sync contacts with this device?"
- Message: explains Kypost can keep contacts in sync with the device's address book,
  that it's off by default, and can be changed later from the Contacts screen.
- Positive button ("Enable"): runs the enable flow (see below).
- Negative button ("Not Now"): no action beyond dismiss.
- Dialog is cancelable; `setOnDismissListener` always marks the flag seen and always
  calls `scanQr()` — covering the positive button, negative button, back-press, and
  tap-outside cases identically. This guarantees the popup fires exactly once per
  install and the user always ends up at the scanner right after, regardless of choice.

New strings in `strings.xml`, following the `contacts_device_sync_*` / `pairing_confirm_*`
naming convention:
- `contact_sync_intro_title`
- `contact_sync_intro_message`
- `contact_sync_intro_positive`
- `contact_sync_intro_negative`

### Shared enable flow
`ContactsListActivity` already has this permission-check → request → enable sequence
as private methods (`checkAndEnableDeviceSync`, `enableDeviceSyncAfterPermissionGrant`).
`PushPairingActivity` needs the identical sequence, so it's extracted into a shared
helper instead of duplicated a second time:

`contacts/device/DeviceContactSyncEnabler.kt`:

```kotlin
class DeviceContactSyncEnabler(
    private val activity: AppCompatActivity,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>,
) {
    fun checkAndEnable() { /* same body as ContactsListActivity.checkAndEnableDeviceSync */ }
    fun enableAfterPermissionGrant() { /* same body as enableDeviceSyncAfterPermissionGrant */ }
}
```

Both activities register their own `ActivityResultLauncher` (must happen before
`STARTED`, so it can't live inside the helper) and construct a `DeviceContactSyncEnabler`
that calls back into `enableAfterPermissionGrant()` on grant. `ContactsListActivity` is
refactored to use it too, so there's one canonical enable path. Toast strings
(`contacts_device_sync_enabled_toast`, `contacts_device_sync_permission_denied`, etc.)
are reused as-is — no new strings needed for the enable path itself.

The intro dialog's positive button calls `DeviceContactSyncEnabler.checkAndEnable()`.

## Out of scope
- No changes to `ContactsListActivity`'s menu-based toggle UI or behavior, beyond
  routing its enable logic through the new shared helper.
- No popup on the `PgpKeyActivity` QR scan flow (contact-adding via PGP key) — confirmed
  scope is the pairing screen's scanner only.
- No new generic "one-time dialog" framework — this is the first such pattern in the
  codebase and is implemented directly, not abstracted for hypothetical future reuse.
- No changes to what device contact syncing does once enabled — this only adds a
  discovery/opt-in moment ahead of an existing, fully-built feature.

## Testing
No Activity/dialog test infrastructure exists in this codebase (confirmed in the prior
contact-delete-confirmation spec). Verification is manual:
1. Fresh install (or clear app data) → open pairing screen → tap "Scan QR" → intro
   dialog appears.
2. Tap "Enable" → permission prompt appears → grant → scanner opens → toast confirms
   sync enabled → `ContactsListActivity`'s menu item now reads "Disable device contact
   sync".
3. Clear data again → tap "Scan QR" → tap "Not Now" → scanner opens directly, sync
   stays off.
4. Clear data again → tap "Scan QR" → back-press / tap outside the dialog → scanner
   opens directly, sync stays off.
5. In all three cases above, tap "Scan QR" again afterward → dialog does not reappear.
6. Regression: exercise `ContactsListActivity`'s existing enable/disable menu toggle
   after the `DeviceContactSyncEnabler` refactor to confirm behavior is unchanged.