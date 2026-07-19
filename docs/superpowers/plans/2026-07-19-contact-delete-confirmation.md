# Contact Delete Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an "Are you sure?" confirmation dialog before `ContactEditActivity` deletes a contact, so an accidental tap on the delete button can no longer destroy data with no recovery path.

**Architecture:** `ContactEditActivity`'s existing delete button (`btnDeleteContact`) currently wires directly to the private `delete()` function, which deletes the contact from the device and queues a server-side delete. This plan changes only the click listener: it now shows an `androidx.appcompat.app.AlertDialog` first, and `delete()` runs only if the user taps the dialog's positive button. `delete()` itself is untouched.

**Tech Stack:** Kotlin, AppCompat `AlertDialog.Builder` (existing pattern, see `PushPairingActivity.kt:232-237`), Android string resources.

## Global Constraints

- No new dependencies — reuse `androidx.appcompat.app.AlertDialog`, already used elsewhere in the app (`PushPairingActivity.kt`, `ComposeActivity.kt`, `pgp/PgpKeyActivity.kt`).
- Follow the existing string-naming convention seen in `pairing_confirm_title` / `pairing_confirm_message` / `pairing_confirm_positive` (`strings.xml:96-99`).
- Reuse the existing `R.string.cancel` resource for the negative button (already used this way at `PushPairingActivity.kt:236`) — do not add a new "cancel" string.
- `delete()`'s body (`ContactEditActivity.kt:503-513`) must not change — only what triggers it.
- No new test framework (Robolectric, Espresso) may be introduced. The codebase has no Activity-level UI test infrastructure today (confirmed: no `androidTest` Activity tests, no Robolectric dependency in `app/build.gradle`), and `ContactEditActivityTest.kt` only exercises the pure `mergedContactDto` function. Adding one is out of scope for this change — verification for this task is manual (build + run the app), not an automated test.

---

### Task 1: Add confirmation-dialog strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml:118` (insert after `contacts_name_required`)

**Interfaces:**
- Produces: string resources `R.string.contacts_delete_confirm_title`, `R.string.contacts_delete_confirm_message`, `R.string.contacts_delete_confirm_positive` — consumed by Task 2.

- [ ] **Step 1: Add the three new string resources**

In `app/src/main/res/values/strings.xml`, immediately after line 118 (`<string name="contacts_name_required">Name is required</string>`), insert:

```xml
    <string name="contacts_delete_confirm_title">Delete contact?</string>
    <string name="contacts_delete_confirm_message">This will permanently delete this contact from this device and the server. This can\'t be undone.</string>
    <string name="contacts_delete_confirm_positive">Delete</string>
```

- [ ] **Step 2: Verify the XML is well-formed**

Run: `./gradlew :app:lintDebug -PlintOnlyChangedFiles 2>/dev/null || ./gradlew :app:processDebugResources`
Expected: `BUILD SUCCESSFUL` (or lint completes with no new errors referencing `strings.xml`). If the specific lint task name doesn't exist in this project, running `./gradlew :app:processDebugResources` alone is sufficient to confirm the resource XML parses.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
feat: add contact delete confirmation strings

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Show confirmation dialog before deleting

**Files:**
- Modify: `app/src/main/java/com/urlxl/mail/contacts/ContactEditActivity.kt:383` (click listener) and imports (top of file, after line 11)

**Interfaces:**
- Consumes: `R.string.contacts_delete_confirm_title`, `R.string.contacts_delete_confirm_message`, `R.string.contacts_delete_confirm_positive` (Task 1), existing `R.string.cancel`, existing private `delete()` function (`ContactEditActivity.kt:503-513`, unchanged).
- Produces: nothing consumed by later tasks — this is the final task.

- [ ] **Step 1: Add the AlertDialog import**

In `app/src/main/java/com/urlxl/mail/contacts/ContactEditActivity.kt`, after line 11 (`import androidx.appcompat.app.AppCompatActivity`), add:

```kotlin
import androidx.appcompat.app.AlertDialog
```

- [ ] **Step 2: Replace the delete button's click listener**

Change line 383 from:

```kotlin
        deleteButton.setOnClickListener { delete() }
```

to:

```kotlin
        deleteButton.setOnClickListener { confirmDelete() }
```

- [ ] **Step 3: Add the `confirmDelete()` function**

Immediately before the existing `private fun delete()` at line 503, add:

```kotlin
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.contacts_delete_confirm_title)
            .setMessage(R.string.contacts_delete_confirm_message)
            .setPositiveButton(R.string.contacts_delete_confirm_positive) { _, _ -> delete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

```

So the two functions now read, in order:

```kotlin
    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.contacts_delete_confirm_title)
            .setMessage(R.string.contacts_delete_confirm_message)
            .setPositiveButton(R.string.contacts_delete_confirm_positive) { _, _ -> delete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun delete() {
        lifecycleScope.launch {
            val deviceGraph = DeviceContactsRuntime.graph(this@ContactEditActivity)
            deviceGraph.repository.deleteDeviceRawContact(existingUid)

            val graph = ContactsRuntime.graph(this@ContactEditActivity)
            graph.repository.queueDelete(existingUid, existingRev)
            graph.coordinator.syncNowAsync()
            finish()
        }
    }
```

- [ ] **Step 4: Build the app module**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` — confirms the new import resolves, `confirmDelete()` compiles, and the string resources from Task 1 are found.

- [ ] **Step 5: Manually verify on a device/emulator**

Since this Activity has no automated UI test coverage in this codebase (see Global Constraints), verify by hand:

1. Install and launch the app: `./gradlew :app:installDebug`, then open it (or use the `run` skill if available for this project).
2. Open an existing contact for edit (so the delete button is visible — it's hidden for new contacts per `existingUid.isBlank()` at line 376-380).
3. Tap the delete button.
4. Confirm: a dialog titled "Delete contact?" appears with the message and "Delete"/"Cancel" buttons — the contact is NOT yet deleted.
5. Tap "Cancel" — confirm: dialog dismisses, contact still exists, still on the edit screen.
6. Tap delete again, then tap "Delete" — confirm: dialog dismisses and the existing delete behavior runs (Activity finishes, contact is gone from the list).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/urlxl/mail/contacts/ContactEditActivity.kt
git commit -m "$(cat <<'EOF'
feat: confirm before deleting a contact

Wraps the existing delete flow in an AlertDialog so an accidental tap
on the delete button no longer destroys a contact with no recovery
path.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review

**Spec coverage:** Spec's "Change" section — dialog on `btnDeleteContact` tap (Task 2 Step 2), title/message/positive/negative copy (Task 1 + Task 2 Step 3), `delete()` unchanged (verified — Task 2 only adds `confirmDelete()` and repoints the listener), new strings following `pairing_confirm_*` convention (Task 1). Spec's "Out of scope" — no new reusable dialog component (confirmed: inlined `AlertDialog.Builder`, matching precedent). Spec's "Testing" section originally proposed an automated `ContactEditActivityTest.kt` addition; investigation during planning found no Activity-level test infrastructure exists in this codebase (no Robolectric, no androidTest Activity tests), so this plan substitutes manual verification (Task 2 Step 5) and documents the deviation in Global Constraints rather than introducing new test infrastructure out of scope for this change.

**Placeholder scan:** No TBD/TODO; all code steps show full code; manual verification steps list concrete pass/fail criteria rather than "test appropriately."

**Type consistency:** `confirmDelete()` takes no arguments and returns `Unit`, matches `setOnClickListener { confirmDelete() }`. String resource names match exactly between Task 1 (definition) and Task 2 (usage): `contacts_delete_confirm_title`, `contacts_delete_confirm_message`, `contacts_delete_confirm_positive`.
