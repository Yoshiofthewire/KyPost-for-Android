# Contact Sync Intro Popup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The first time a user taps "Scan QR" on the pairing screen (`PushPairingActivity`), show a one-time popup explaining device contact syncing (off by default) and letting them opt in, then continue to the QR scanner either way.

**Architecture:** A new `hasShownSyncIntro` flag is added to the existing `DeviceContactSyncSettings` (same `SharedPreferences` file as the `enabled` flag). `PushPairingActivity`'s scan button checks that flag before calling the existing `scanQr()`; if unset, it shows an `AlertDialog` first. The permission-check → request → enable sequence that `ContactsListActivity` already has as private methods is extracted into a shared `DeviceContactSyncEnabler` class so both activities use one canonical enable path instead of two copies of the same logic.

**Tech Stack:** Kotlin, AppCompat `AlertDialog.Builder`, `SharedPreferences`, `androidx.activity.result.ActivityResultLauncher`.

## Global Constraints

- No new dependencies — reuse `androidx.appcompat.app.AlertDialog` and existing `SharedPreferences`-backed settings pattern.
- Follow the existing string-naming convention seen in `contacts_device_sync_*` (`strings.xml:197-201`) and `pairing_confirm_*` (`strings.xml:96-99`).
- No new test framework may be introduced. This codebase has no Robolectric dependency and no Activity-level UI test infrastructure (confirmed by the prior contact-delete-confirmation plan and spec). `DeviceContactSyncSettings` requires an Android `Context` (`context.getSharedPreferences`), so it cannot be exercised in a plain JVM unit test either. Verification for every task in this plan is manual (build + run on a device/emulator), same as the precedent set in `docs/superpowers/plans/2026-07-19-contact-delete-confirmation.md`.
- **Deviation from the spec's exact dialog-dismissal wording:** the spec's `## Change` section says "`setOnDismissListener` always ... always calls `scanQr()`". Implementing it literally that way races two system UIs: `DeviceContactSyncEnabler.checkAndEnable()`'s permission request (an async activity launch) and `scanQr()`'s ML Kit scanner (also an async activity launch) would both fire back-to-back when the user taps "Enable" without permissions yet granted. This plan instead calls `scanQr()` from whichever path actually finishes last: immediately in the negative-button and cancel handlers, but only after the permission launcher's callback resolves when a permission request was needed (`checkAndEnable()` returns `true` in that case). `setOnDismissListener` still runs on every path and is solely responsible for marking `hasShownSyncIntro = true`, so the "shown exactly once, ends up at the scanner" behavior the spec describes is preserved — only the internal sequencing changes to avoid overlapping system dialogs.

---

### Task 1: Add the one-time-shown flag to `DeviceContactSyncSettings`

**Files:**
- Modify: `app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncSettings.kt`

**Interfaces:**
- Produces: `DeviceContactSyncSettings.hasShownSyncIntro(): Boolean` and `DeviceContactSyncSettings.setHasShownSyncIntro(shown: Boolean)` — consumed by Task 4.

- [ ] **Step 1: Add the new methods and key**

Replace the full contents of `app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncSettings.kt` with:

```kotlin
package com.urlxl.mail.contacts.device

import android.content.Context
import android.content.SharedPreferences

class DeviceContactSyncSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lastForeignScanAtEpochMs(): Long = prefs.getLong(KEY_LAST_FOREIGN_SCAN, 0L)

    fun setLastForeignScanAtEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_FOREIGN_SCAN, epochMs).apply()
    }

    fun hasShownSyncIntro(): Boolean = prefs.getBoolean(KEY_SHOWN_INTRO, false)

    fun setHasShownSyncIntro(shown: Boolean) {
        prefs.edit().putBoolean(KEY_SHOWN_INTRO, shown).apply()
    }

    companion object {
        private const val PREFS_NAME = "com.urlxl.mail.device_contacts"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_FOREIGN_SCAN = "last_foreign_scan_epoch_ms"
        private const val KEY_SHOWN_INTRO = "has_shown_sync_intro"
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncSettings.kt
git commit -m "$(cat <<'EOF'
feat: add hasShownSyncIntro flag to DeviceContactSyncSettings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Add intro-dialog strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml:201` (insert after `contacts_device_sync_disabled_toast`)

**Interfaces:**
- Produces: `R.string.contact_sync_intro_title`, `R.string.contact_sync_intro_message`, `R.string.contact_sync_intro_positive`, `R.string.contact_sync_intro_negative` — consumed by Task 4.

- [ ] **Step 1: Add the four new string resources**

In `app/src/main/res/values/strings.xml`, immediately after line 201 (`<string name="contacts_device_sync_disabled_toast">Device contact sync disabled</string>`), insert:

```xml
    <string name="contact_sync_intro_title">Sync contacts with this device?</string>
    <string name="contact_sync_intro_message">Kypost can keep your contacts in sync with this device\'s address book. This is off by default and only happens if you turn it on — you can change it anytime from the Contacts screen.</string>
    <string name="contact_sync_intro_positive">Enable</string>
    <string name="contact_sync_intro_negative">Not Now</string>
```

- [ ] **Step 2: Verify the XML is well-formed**

Run: `./gradlew :app:processDebugResources`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat: add contact sync intro popup strings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Extract `DeviceContactSyncEnabler` and refactor `ContactsListActivity` to use it

**Files:**
- Create: `app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncEnabler.kt`
- Modify: `app/src/main/java/com/urlxl/mail/contacts/ContactsListActivity.kt`

**Interfaces:**
- Consumes: `DeviceContactsRuntime.graph(Context): DeviceContactsGraph` (existing, `DeviceContactsGraph.kt:27-31`), `DeviceContactSyncScheduler.ensurePeriodic(Context)` (existing, `DeviceContactSyncWorker.kt:33`), `DeviceContactSyncSettings.setEnabled` (existing).
- Produces: `DeviceContactSyncEnabler(activity: AppCompatActivity, permissionLauncher: ActivityResultLauncher<Array<String>>, onEnabled: () -> Unit = {})` with `fun checkAndEnable(): Boolean` (returns `true` if it launched an async permission request; `false` if it resolved synchronously) and `fun enableAfterPermissionGrant()` — both consumed by Task 4.

- [ ] **Step 1: Create `DeviceContactSyncEnabler.kt`**

```kotlin
package com.urlxl.mail.contacts.device

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.urlxl.mail.R
import kotlinx.coroutines.launch

/** Shared permission-check -> request -> enable sequence for device contact sync, used by both
 *  the Contacts screen's menu toggle and the pairing screen's first-scan intro popup so there's
 *  one canonical enable path instead of two copies of this logic. */
class DeviceContactSyncEnabler(
    private val activity: AppCompatActivity,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>,
    private val onEnabled: () -> Unit = {},
) {
    /** Returns true if permissions had to be requested asynchronously — the caller must wait for
     *  the permission launcher's own callback (which should call [enableAfterPermissionGrant] on
     *  grant) before doing anything that assumes sync is now enabled. Returns false if it
     *  resolved synchronously because permissions were already granted. */
    fun checkAndEnable(): Boolean {
        val readContactsGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        val writeContactsGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

        return if (readContactsGranted && writeContactsGranted) {
            enableAfterPermissionGrant()
            false
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                ),
            )
            true
        }
    }

    fun enableAfterPermissionGrant() {
        val graph = DeviceContactsRuntime.graph(activity)
        activity.lifecycleScope.launch {
            try {
                graph.accountManager.ensureAccount()
                graph.settings.setEnabled(true)
                graph.observer.register()
                DeviceContactSyncScheduler.ensurePeriodic(activity)
                graph.coordinator.syncNowAsync()
                Toast.makeText(activity, R.string.contacts_device_sync_enabled_toast, Toast.LENGTH_SHORT).show()
                onEnabled()
            } catch (e: Exception) {
                Toast.makeText(activity, "Failed to enable device sync: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
```

- [ ] **Step 2: Add the import in `ContactsListActivity.kt`**

After line 26 (`import com.urlxl.mail.contacts.device.DeviceContactsRuntime`), add:

```kotlin
import com.urlxl.mail.contacts.device.DeviceContactSyncEnabler
```

- [ ] **Step 3: Replace the permission launcher and add the shared enabler property**

Change (lines 37-46):

```kotlin
    private val contactPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, R.string.contacts_device_sync_permission_denied, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        enableDeviceSyncAfterPermissionGrant()
    }
```

to:

```kotlin
    private val contactPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, R.string.contacts_device_sync_permission_denied, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        syncEnabler.enableAfterPermissionGrant()
    }
    private val syncEnabler = DeviceContactSyncEnabler(
        activity = this,
        permissionLauncher = contactPermissionLauncher,
        onEnabled = { invalidateOptionsMenu() },
    )
```

- [ ] **Step 4: Route the menu handler through the shared enabler**

Change (lines 178-186):

```kotlin
            MENU_DEVICE_SYNC -> {
                val graph = DeviceContactsRuntime.graph(this)
                if (graph.settings.isEnabled()) {
                    disableDeviceSync()
                } else {
                    checkAndEnableDeviceSync()
                }
                true
            }
```

to:

```kotlin
            MENU_DEVICE_SYNC -> {
                val graph = DeviceContactsRuntime.graph(this)
                if (graph.settings.isEnabled()) {
                    disableDeviceSync()
                } else {
                    syncEnabler.checkAndEnable()
                }
                true
            }
```

- [ ] **Step 5: Remove the now-duplicated private methods**

Delete the `checkAndEnableDeviceSync()` and `enableDeviceSyncAfterPermissionGrant()` private functions (originally lines 212-257, immediately before `disableDeviceSync()`). `disableDeviceSync()` itself is unchanged and stays.

- [ ] **Step 6: Remove now-unused imports**

`Manifest`, `PackageManager`, and `ContextCompat` were only used inside the two functions removed in Step 5 (confirmed: `grep -n "Manifest\.\|PackageManager\.\|ContextCompat\." app/src/main/java/com/urlxl/mail/contacts/ContactsListActivity.kt` shows no other usages). Remove these three import lines:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
```

- [ ] **Step 7: Build the app module**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Manually verify the existing Contacts-screen toggle still works (regression check)**

1. Install and launch: `./gradlew :app:installDebug`, open the app, navigate to the Contacts screen.
2. Open the overflow menu — confirm "Enable device contact sync" is shown (assuming sync starts disabled).
3. Tap it. If prompted, grant the READ_CONTACTS/WRITE_CONTACTS permissions.
4. Confirm: a "Device contact sync enabled" toast appears, and the overflow menu now reads "Disable device contact sync".
5. Tap it again — confirm: "Device contact sync disabled" toast appears, menu reverts to "Enable device contact sync".

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncEnabler.kt app/src/main/java/com/urlxl/mail/contacts/ContactsListActivity.kt
git commit -m "$(cat <<'EOF'
refactor: extract DeviceContactSyncEnabler from ContactsListActivity

Pulls the permission-check -> request -> enable sequence out of
ContactsListActivity into a shared class so the pairing screen's
upcoming intro popup can reuse the same enable path instead of a
second copy of this logic.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Show the intro popup before the first QR scan in `PushPairingActivity`

**Files:**
- Modify: `app/src/main/java/com/urlxl/mail/push/PushPairingActivity.kt`

**Interfaces:**
- Consumes: `DeviceContactSyncEnabler` and its `checkAndEnable(): Boolean` / `enableAfterPermissionGrant()` (Task 3), `DeviceContactSyncSettings.hasShownSyncIntro()` / `setHasShownSyncIntro(Boolean)` (Task 1), `R.string.contact_sync_intro_title` / `_message` / `_positive` / `_negative` (Task 2), `DeviceContactsRuntime.graph(Context)` (existing), existing private `scanQr()` (unchanged, `PushPairingActivity.kt:199-212`).
- Produces: nothing consumed by later tasks — this is the final task.

- [ ] **Step 1: Add imports**

After line 24 (`import com.urlxl.mail.R`), add:

```kotlin
import com.urlxl.mail.contacts.device.DeviceContactSyncEnabler
import com.urlxl.mail.contacts.device.DeviceContactSyncSettings
import com.urlxl.mail.contacts.device.DeviceContactsRuntime
```

- [ ] **Step 2: Add the permission launcher and shared enabler properties**

Change (lines 61-68):

```kotlin
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications disabled; push still arrives in-app history", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
```

to:

```kotlin
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications disabled; push still arrives in-app history", Toast.LENGTH_SHORT).show()
        }
    }

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            syncEnabler.enableAfterPermissionGrant()
        } else {
            Toast.makeText(this, R.string.contacts_device_sync_permission_denied, Toast.LENGTH_SHORT).show()
        }
        // Whether or not sync got enabled, this is the resolution of the permission flow the
        // intro popup kicked off — safe to continue to the scanner now that it's settled.
        scanQr()
    }

    private val syncEnabler = DeviceContactSyncEnabler(
        activity = this,
        permissionLauncher = contactPermissionLauncher,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
```

- [ ] **Step 3: Route the scan button through the intro-popup check**

Change line 86 from:

```kotlin
        btnScanQr.setOnClickListener { scanQr() }
```

to:

```kotlin
        btnScanQr.setOnClickListener { onScanQrClicked() }
```

- [ ] **Step 4: Add `onScanQrClicked()` and `showSyncIntroDialog()`**

Immediately before the existing `private fun scanQr()` (line 199), add:

```kotlin
    private fun onScanQrClicked() {
        val settings = DeviceContactsRuntime.graph(this).settings
        if (settings.hasShownSyncIntro()) {
            scanQr()
        } else {
            showSyncIntroDialog(settings)
        }
    }

    private fun showSyncIntroDialog(settings: DeviceContactSyncSettings) {
        AlertDialog.Builder(this)
            .setTitle(R.string.contact_sync_intro_title)
            .setMessage(R.string.contact_sync_intro_message)
            .setPositiveButton(R.string.contact_sync_intro_positive) { _, _ ->
                // If this needs to request permissions, contactPermissionLauncher's callback
                // calls scanQr() once that resolves. Calling it here too would launch the QR
                // scanner on top of the still-open system permission dialog.
                val requestedPermission = syncEnabler.checkAndEnable()
                if (!requestedPermission) scanQr()
            }
            .setNegativeButton(R.string.contact_sync_intro_negative) { _, _ -> scanQr() }
            .setOnCancelListener { scanQr() }
            .setOnDismissListener { settings.setHasShownSyncIntro(true) }
            .show()
    }

```

So the three functions now read, in order: `onScanQrClicked()`, `showSyncIntroDialog()`, `scanQr()` (unchanged).

- [ ] **Step 5: Build the app module**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Manually verify on a device/emulator**

No automated Activity/dialog test infrastructure exists in this codebase (see Global Constraints), so verify by hand. Use `./gradlew :app:installDebug` and clear app data between runs (Settings → Apps → Kypost → Storage → Clear data, or uninstall/reinstall) to reset the one-time flag.

1. Fresh data → open the pairing screen → tap "Scan QR" → confirm the "Sync contacts with this device?" dialog appears (scanner does NOT open yet).
2. Tap "Enable" → confirm the READ_CONTACTS/WRITE_CONTACTS permission prompt appears → grant it → confirm the QR scanner opens right after, and a "Device contact sync enabled" toast appears (either before or shortly after the scanner opens).
3. Cancel out of the scanner. Go to the Contacts screen's overflow menu — confirm it now reads "Disable device contact sync".
4. Clear app data again → tap "Scan QR" → tap "Not Now" → confirm the QR scanner opens directly, with no permission prompt and no enabled toast.
5. Clear app data again → tap "Scan QR" → press the system back button (or tap outside the dialog) → confirm the QR scanner opens directly.
6. In each of the three scenarios above, cancel the scanner and tap "Scan QR" again → confirm the dialog does NOT reappear (it already fired once for that install).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/urlxl/mail/push/PushPairingActivity.kt
git commit -m "$(cat <<'EOF'
feat: show contact sync intro popup before first pairing QR scan

The first time a user taps "Scan QR" on the pairing screen, they now
see a one-time popup explaining device contact syncing (off by
default) with the option to enable it, before the scanner opens.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage:** One-time trigger gated by `hasShownSyncIntro` (Task 1, wired in Task 4 Step 4). Dialog content/buttons and cancel-counts-as-decline behavior (Task 4 Step 4, `setOnCancelListener`/`setOnDismissListener`). Enable path reusing the existing permission→enable sequence (Task 3's `DeviceContactSyncEnabler`, consumed in Task 4). Auto-continue to scanner regardless of choice (Task 4 Step 2 launcher callback + Step 4 button/cancel handlers — see the documented sequencing deviation in Global Constraints). Shared enabler refactor of `ContactsListActivity` (Task 3). New strings following existing naming convention (Task 2). Out-of-scope items (no `PgpKeyActivity` popup, no generic one-time-dialog framework, no change to what enabled sync does) — none of the tasks touch those areas, confirmed by file list.

**Placeholder scan:** No TBD/TODO; every code step shows complete code; manual verification steps list concrete, checkable outcomes rather than "test appropriately."

**Type consistency:** `DeviceContactSyncEnabler.checkAndEnable(): Boolean` is defined in Task 3 Step 1 and consumed with that exact return type in Task 4 Step 4 (`val requestedPermission = syncEnabler.checkAndEnable()`). `DeviceContactSyncSettings.hasShownSyncIntro()` / `setHasShownSyncIntro(Boolean)` names match exactly between Task 1 (definition) and Task 4 (usage). String resource names (`contact_sync_intro_title/_message/_positive/_negative`) match exactly between Task 2 (definition) and Task 4 (usage). `onEnabled` callback parameter on `DeviceContactSyncEnabler` is used in Task 3 (`{ invalidateOptionsMenu() }`) and left at its default `{}` in Task 4, both valid per the `= {}` default in the Task 3 constructor.
