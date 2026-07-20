# Remaining Llama Naming Cleanup (kypost-android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every remaining "Llama"/"llama" brand identifier from the kypost-android app — 5 `Llama*`-named Kotlin classes, 3 XML style names, and 7 string constants (notification channels, storage keys, WorkManager job names, the local Room database filename) — leaving only genuinely unrelated content untouched.

**Architecture:** Pure rename/refactor, no behavior change, except the Room database filename rename (confirmed safe: app is not installed anywhere with real user data to preserve).

**Tech Stack:** Kotlin, Android (Gradle), Room, WorkManager.

## Global Constraints

- Confirmed: this app is not installed anywhere with real user data. The Room database filename rename (`llama_mail.db` → `kypost_mail.db`) is a direct rename, no on-device migration needed. (If that assumption changes before this executes, stop and add a migration step — don't silently proceed, per the pattern used elsewhere in this rebrand.)
- New naming: `Llama` → `KyPost` (classes, styles), matching the app's existing `KyPost` branding (`R.string.app_name` is already `"KyPost"`).
- Every rename in this plan is scoped to an explicit file list — no blind repo-wide `llama`→`kypost` regex, since some files (androidTest resource-id references, doc comments) need the renamed identifier to match exactly what earlier tasks in this plan produce.
- Build/verification baseline: `./gradlew testDebugUnitTest assembleDebug` must succeed after every task (and `./gradlew connectedAndroidTest` is unavailable without a device/emulator — note as unverified if it can't run, don't skip the task over it).

---

## Task 1: Rename the 5 `Llama*` Kotlin classes and their files

**Files:**
- Move: `app/src/main/java/com/urlxl/mail/LlamaApp.kt` → `KyPostApp.kt`
- Move: `app/src/main/java/com/urlxl/mail/push/LlamaFirebaseMessagingService.kt` → `KyPostFirebaseMessagingService.kt`
- Move: `app/src/main/java/com/urlxl/mail/push/LlamaUnifiedPushService.kt` → `KyPostUnifiedPushService.kt`
- Move: `app/src/main/java/com/urlxl/mail/contacts/device/LlamaContactAuthenticator.kt` → `KyPostContactAuthenticator.kt`
- Move: `app/src/main/java/com/urlxl/mail/contacts/device/LlamaContactAuthenticatorService.kt` → `KyPostContactAuthenticatorService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (4 `android:name` references)
- Modify: `app/src/main/java/com/urlxl/mail/push/UnifiedPushRegistrar.kt` (1 comment)
- Modify: `app/src/main/java/com/urlxl/mail/push/PullWorker.kt` (1 comment)
- Modify: `app/src/main/java/com/urlxl/mail/push/PushHomeViewModel.kt` (1 comment)
- Modify: `app/src/main/java/com/urlxl/mail/data/DataRuntime.kt` (1 comment)

**Interfaces:**
- Produces: `KyPostApp`, `KyPostFirebaseMessagingService`, `KyPostUnifiedPushService`, `KyPostContactAuthenticator`, `KyPostContactAuthenticatorService` — consumed by Task 2's XML edits (AndroidManifest theme lives on the same `<application>` tag).

- [ ] **Step 1: Move the 5 files**

Run:
```bash
cd /home/yoshi/git/kypost-android
git mv app/src/main/java/com/urlxl/mail/LlamaApp.kt app/src/main/java/com/urlxl/mail/KyPostApp.kt
git mv app/src/main/java/com/urlxl/mail/push/LlamaFirebaseMessagingService.kt app/src/main/java/com/urlxl/mail/push/KyPostFirebaseMessagingService.kt
git mv app/src/main/java/com/urlxl/mail/push/LlamaUnifiedPushService.kt app/src/main/java/com/urlxl/mail/push/KyPostUnifiedPushService.kt
git mv app/src/main/java/com/urlxl/mail/contacts/device/LlamaContactAuthenticator.kt app/src/main/java/com/urlxl/mail/contacts/device/KyPostContactAuthenticator.kt
git mv app/src/main/java/com/urlxl/mail/contacts/device/LlamaContactAuthenticatorService.kt app/src/main/java/com/urlxl/mail/contacts/device/KyPostContactAuthenticatorService.kt
```

- [ ] **Step 2: Rename the class declarations and every in-file self-reference (log tags, etc.) in each moved file**

Run:
```bash
sed -i 's/\bLlamaApp\b/KyPostApp/g' app/src/main/java/com/urlxl/mail/KyPostApp.kt
sed -i 's/\bLlamaFirebaseMessagingService\b/KyPostFirebaseMessagingService/g' app/src/main/java/com/urlxl/mail/push/KyPostFirebaseMessagingService.kt
sed -i 's/\bLlamaUnifiedPushService\b/KyPostUnifiedPushService/g' app/src/main/java/com/urlxl/mail/push/KyPostUnifiedPushService.kt
sed -i 's/\bLlamaContactAuthenticator\b/KyPostContactAuthenticator/g; s/\bLlamaContactAuthenticatorService\b/KyPostContactAuthenticatorService/g' app/src/main/java/com/urlxl/mail/contacts/device/KyPostContactAuthenticator.kt app/src/main/java/com/urlxl/mail/contacts/device/KyPostContactAuthenticatorService.kt
```

**Watch out:** `KyPostContactAuthenticatorService.kt` references both `LlamaContactAuthenticatorService` (its own class name) and `LlamaContactAuthenticator` (a different class it instantiates) — the `sed` above handles both correctly since it's a single command with both patterns applied to both files, but verify in Step 6 that `KyPostContactAuthenticatorService` didn't accidentally end up referencing `LlamaContactAuthenticator` un-renamed (order of the two `s///` clauses doesn't matter here since the two old names don't overlap as substrings).

- [ ] **Step 3: Update the 4 `android:name` references in AndroidManifest.xml**

Run:
```bash
sed -i \
  -e 's/android:name="\.LlamaApp"/android:name=".KyPostApp"/' \
  -e 's/android:name="\.push\.LlamaFirebaseMessagingService"/android:name=".push.KyPostFirebaseMessagingService"/' \
  -e 's/android:name="\.push\.LlamaUnifiedPushService"/android:name=".push.KyPostUnifiedPushService"/' \
  -e 's/android:name="\.contacts\.device\.LlamaContactAuthenticatorService"/android:name=".contacts.device.KyPostContactAuthenticatorService"/' \
  app/src/main/AndroidManifest.xml
```

- [ ] **Step 4: Update the 4 comment-only references in other files**

Run:
```bash
sed -i 's/LlamaUnifiedPushService/KyPostUnifiedPushService/' app/src/main/java/com/urlxl/mail/push/UnifiedPushRegistrar.kt app/src/main/java/com/urlxl/mail/push/PushHomeViewModel.kt
sed -i 's/\[LlamaApp\]/[KyPostApp]/' app/src/main/java/com/urlxl/mail/push/PullWorker.kt
sed -i 's/PushGraph\/LlamaApp/PushGraph\/KyPostApp/' app/src/main/java/com/urlxl/mail/data/DataRuntime.kt
```

- [ ] **Step 5: Verify zero remaining `Llama` class references**

Run: `grep -rn "Llama" app/src --include="*.kt" --include="*.xml" | grep -v "kypost-labels/frontend"`
Expected: no output except possibly the `DeviceContactFieldCoding.kt` comment referencing a doc path `llama-labels/frontend/src/api/contacts.ts` — that one is out of scope for this task (it's a cross-repo doc-path reference, handled in Task 3 alongside the other doc comments).

- [ ] **Step 6: Build and run unit tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/urlxl/mail/KyPostApp.kt app/src/main/java/com/urlxl/mail/push/KyPostFirebaseMessagingService.kt app/src/main/java/com/urlxl/mail/push/KyPostUnifiedPushService.kt app/src/main/java/com/urlxl/mail/contacts/device/KyPostContactAuthenticator.kt app/src/main/java/com/urlxl/mail/contacts/device/KyPostContactAuthenticatorService.kt app/src/main/AndroidManifest.xml app/src/main/java/com/urlxl/mail/push/UnifiedPushRegistrar.kt app/src/main/java/com/urlxl/mail/push/PullWorker.kt app/src/main/java/com/urlxl/mail/push/PushHomeViewModel.kt app/src/main/java/com/urlxl/mail/data/DataRuntime.kt
git commit -m "android: rename Llama*-prefixed classes to KyPost*"
```

---

## Task 2: Rename XML style resources and their references

**Files:**
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/layout/activity_inbox.xml`
- Modify: `app/src/main/res/layout/activity_compose.xml`
- Modify: `app/src/androidTest/java/com/urlxl/mail/contacts/ExpandableSectionViewTest.kt`
- Modify: `app/src/androidTest/java/com/urlxl/mail/contacts/RepeatableFieldListTest.kt`

**Interfaces:**
- Produces: styles `Theme.KyPost`, `TextAppearance.KyPost.BottomNavLabel`, `Widget.KyPost.IconOnlyChip` — the generated Kotlin resource IDs (`R.style.Theme_KyPost`, etc.) are consumed by the two androidTest files.

- [ ] **Step 1: Rename the 3 style declarations in themes.xml**

Run:
```bash
cd /home/yoshi/git/kypost-android
sed -i \
  -e 's/name="Theme\.LlamaMailForAndroid"/name="Theme.KyPost"/' \
  -e 's/name="TextAppearance\.Llama\.BottomNavLabel"/name="TextAppearance.KyPost.BottomNavLabel"/' \
  -e 's/name="Widget\.Llama\.IconOnlyChip"/name="Widget.KyPost.IconOnlyChip"/' \
  app/src/main/res/values/themes.xml
```

- [ ] **Step 2: Update every reference to the renamed styles**

Run:
```bash
sed -i 's/@style\/Theme\.LlamaMailForAndroid/@style\/Theme.KyPost/' app/src/main/AndroidManifest.xml
sed -i 's/@style\/TextAppearance\.Llama\.BottomNavLabel/@style\/TextAppearance.KyPost.BottomNavLabel/g' app/src/main/res/layout/activity_inbox.xml
sed -i 's/@style\/Widget\.Llama\.IconOnlyChip/@style\/Widget.KyPost.IconOnlyChip/g' app/src/main/res/layout/activity_compose.xml
```

- [ ] **Step 3: Update the generated resource-ID references in the two androidTest files**

Run:
```bash
sed -i 's/R\.style\.Theme_LlamaMailForAndroid/R.style.Theme_KyPost/' app/src/androidTest/java/com/urlxl/mail/contacts/RepeatableFieldListTest.kt
sed -i \
  -e 's/R\.style\.Theme_LlamaMailForAndroid/R.style.Theme_KyPost/' \
  -e 's/@style="@style\/Theme\.LlamaMailForAndroid"/@style="@style\/Theme.KyPost"/' \
  -e "s/Theme\.LlamaMailForAndroid/Theme.KyPost/" \
  app/src/androidTest/java/com/urlxl/mail/contacts/ExpandableSectionViewTest.kt
```

- [ ] **Step 4: Verify zero remaining style-name references**

Run: `grep -rn "Llama" app/src/main/res app/src/androidTest --include="*.xml" --include="*.kt"`
Expected: no output.

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. (androidTest itself needs a device/emulator to actually run — `connectedAndroidTest` — note in your report if none is available; that's expected in this environment, not a failure of this task.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/themes.xml app/src/main/AndroidManifest.xml app/src/main/res/layout/activity_inbox.xml app/src/main/res/layout/activity_compose.xml app/src/androidTest/java/com/urlxl/mail/contacts/ExpandableSectionViewTest.kt app/src/androidTest/java/com/urlxl/mail/contacts/RepeatableFieldListTest.kt
git commit -m "android: rename Llama-prefixed style resources to KyPost"
```

---

## Task 3: Rename string constants, identifiers, and doc-comment references

**Files:**
- Modify: `app/src/main/java/com/urlxl/mail/push/PushNotificationDispatcher.kt`
- Modify: `app/src/main/java/com/urlxl/mail/AppTheme.kt`
- Modify: `app/src/main/java/com/urlxl/mail/ComposeActivity.kt`
- Modify: `app/src/main/java/com/urlxl/mail/push/PullWorker.kt`
- Modify: `app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncWorker.kt`
- Modify: `app/src/main/java/com/urlxl/mail/data/DataRuntime.kt`
- Modify: `app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactFieldCoding.kt`
- Modify: `app/src/main/java/com/urlxl/mail/push/PullSyncCoordinator.kt`

**Interfaces:**
- Produces: notification channel IDs `kypost_push`/`kypost_mfa` (channel display name `"KyPost"`), theme storage key `"kypost-theme"`, compose-editor CSS id `"kypost-compose-theme"`, WorkManager unique job names `kypost_pull_periodic`/`kypost_device_contact_sync_periodic`, Room database filename `kypost_mail.db`.

- [ ] **Step 1: Rename the notification channel IDs and display name**

Run:
```bash
cd /home/yoshi/git/kypost-android
sed -i \
  -e 's/"llama_labels_push"/"kypost_push"/' \
  -e 's/"llama_labels_mfa"/"kypost_mfa"/' \
  -e 's/"Llama Labels"/"KyPost"/' \
  app/src/main/java/com/urlxl/mail/push/PushNotificationDispatcher.kt
```

**Note:** Android treats a channel ID change as a brand-new channel — any user who had muted/customized the old `llama_labels_push`/`llama_labels_mfa` channels will see default settings on the new channels. This is expected and matches how the rest of this rebrand handled similar non-destructive identifier changes; it does not lose any app data (unlike the database filename in Step 5, which was separately confirmed safe to rename outright).

- [ ] **Step 2: Rename the theme storage key**

Run:
```bash
sed -i 's/"llama-lab-theme"/"kypost-theme"/' app/src/main/java/com/urlxl/mail/AppTheme.kt
```

- [ ] **Step 3: Rename the Compose editor CSS id**

Run:
```bash
sed -i 's/id = "llama-theme"/id = "kypost-compose-theme"/' app/src/main/java/com/urlxl/mail/ComposeActivity.kt
```

- [ ] **Step 4: Rename the WorkManager unique work names**

Run:
```bash
sed -i 's/"llama_pull_periodic"/"kypost_pull_periodic"/' app/src/main/java/com/urlxl/mail/push/PullWorker.kt
sed -i 's/"llama_device_contact_sync_periodic"/"kypost_device_contact_sync_periodic"/' app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncWorker.kt
```

- [ ] **Step 5: Rename the Room database filename**

Run:
```bash
sed -i 's/"llama_mail\.db"/"kypost_mail.db"/' app/src/main/java/com/urlxl/mail/data/DataRuntime.kt
```

Confirmed safe (see Global Constraints): this app has no installs anywhere with data to preserve, so this is a direct rename — the next app launch creates a fresh `kypost_mail.db` with no migration needed.

- [ ] **Step 6: Update the two remaining doc-comment references**

Run:
```bash
sed -i "s#\`llama-labels/frontend/src/api/contacts.ts\`#\`kypost-server/frontend/src/api/contacts.ts\`#" app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactFieldCoding.kt
sed -i 's/Llama Labels server/KyPost server/' app/src/main/java/com/urlxl/mail/push/PullSyncCoordinator.kt
```

- [ ] **Step 7: Verify zero remaining "llama" references in the 8 touched files**

Run: `grep -n -i "llama" app/src/main/java/com/urlxl/mail/push/PushNotificationDispatcher.kt app/src/main/java/com/urlxl/mail/AppTheme.kt app/src/main/java/com/urlxl/mail/ComposeActivity.kt app/src/main/java/com/urlxl/mail/push/PullWorker.kt app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncWorker.kt app/src/main/java/com/urlxl/mail/data/DataRuntime.kt app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactFieldCoding.kt app/src/main/java/com/urlxl/mail/push/PullSyncCoordinator.kt`
Expected: no output.

- [ ] **Step 8: Build and test**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/urlxl/mail/push/PushNotificationDispatcher.kt app/src/main/java/com/urlxl/mail/AppTheme.kt app/src/main/java/com/urlxl/mail/ComposeActivity.kt app/src/main/java/com/urlxl/mail/push/PullWorker.kt app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactSyncWorker.kt app/src/main/java/com/urlxl/mail/data/DataRuntime.kt app/src/main/java/com/urlxl/mail/contacts/device/DeviceContactFieldCoding.kt app/src/main/java/com/urlxl/mail/push/PullSyncCoordinator.kt
git commit -m "android: rename remaining llama-branded string constants and doc references"
```

---

## Task 4: Final full-repo sweep

**Files:** none (verification only)

- [ ] **Step 1: Full case-insensitive sweep**

Run:
```bash
cd /home/yoshi/git/kypost-android
grep -rni "llama" app/src --include="*.kt" --include="*.xml"
```
Expected: no output.

- [ ] **Step 2: Full build and unit test suite**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: `BUILD SUCCESSFUL`, all unit tests pass.
