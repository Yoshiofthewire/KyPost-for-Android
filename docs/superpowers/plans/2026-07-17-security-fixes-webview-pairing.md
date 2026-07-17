# Security Fixes: WebView XSS and Deep-Link Pairing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix two Critical security findings: JavaScript execution enabled on untrusted email HTML in `EmailDetailActivity`'s WebView, and the exported `llamalabels://native-pair` deep link silently applying an attacker-controlled pairing with no user confirmation.

**Architecture:** Fix 1 is a one-line settings change. Fix 2 extracts a shared `applyParsedPairing` suspend helper in `PushHomeViewModel` (used by both the existing QR-scan path and a new deep-link path), and adds a confirmation `AlertDialog` in `PushPairingActivity` that only the deep-link entry point goes through.

**Tech Stack:** Kotlin, `android.webkit.WebView`, `androidx.appcompat.app.AlertDialog`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-17-security-fixes-webview-pairing-design.md`.
- QR-scan pairing (`scanQr()`/`PushPairingActivity.kt:198-211`) must keep applying immediately, with no new dialog — only the deep-link path changes.
- `AndroidManifest.xml` is not touched — `PushPairingActivity` stays `exported="true"`.
- No HTML sanitizer library, no image/tracking-pixel blocking changes.
- No automated test covers either UI flow (manual-only, matching this codebase's existing convention for `InboxActivity`'s and `PushPairingActivity`'s UI wiring).
- Build command: `./gradlew assembleDebug` from the repo root.

---

### Task 1: Disable JavaScript in the email-reading WebView

**Files:**
- Modify: `app/src/main/java/com/urlxl/mail/EmailDetailActivity.kt`

**Interfaces:** None — this task is self-contained, no other task depends on it.

- [ ] **Step 1: Flip `javaScriptEnabled` to `false`**

  In `app/src/main/java/com/urlxl/mail/EmailDetailActivity.kt`, the `webView.settings.apply { ... }` block currently reads (`EmailDetailActivity.kt:120-127`):

  ```kotlin
      webView.settings.apply {
          javaScriptEnabled = true
          builtInZoomControls = true
          displayZoomControls = false
          useWideViewPort = true
          loadWithOverviewMode = true
          defaultTextEncodingName = "utf-8"
      }
  ```

  becomes:

  ```kotlin
      webView.settings.apply {
          javaScriptEnabled = false
          builtInZoomControls = true
          displayZoomControls = false
          useWideViewPort = true
          loadWithOverviewMode = true
          defaultTextEncodingName = "utf-8"
      }
  ```

- [ ] **Step 2: Build**

  Run: `./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Manually verify on a device/emulator**

  No automated test covers this flow. Install and check by hand:
  - Open any email with an HTML body — confirm normal markup (bold, links, images, layout) still renders correctly. Only script execution is disabled, not HTML rendering.
  - If you can craft or receive a test email containing `<script>alert(1)</script>` in the body, open it and confirm no JS alert fires.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/urlxl/mail/EmailDetailActivity.kt
  git commit -m "fix: disable JavaScript in the email body WebView (XSS)"
  ```

---

### Task 2: Confirm before applying a deep-link pairing

**Files:**
- Modify: `app/src/main/java/com/urlxl/mail/push/PushHomeViewModel.kt`
- Modify: `app/src/main/java/com/urlxl/mail/push/PushPairingActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `PushHomeViewModel.applyDeepLinkPairing(pairing: PairingData)` — public method, called only from `PushPairingActivity.confirmAndApplyDeepLinkPairing`'s dialog positive button.
- Consumes: `NativePairingDeepLinkParser.parse(link: String): PairingParseResult` and `PairingData`/`PairingParseResult` (all pre-existing, in `PairingModels.kt`, same `com.urlxl.mail.push` package as both modified files — no new imports needed for these).

- [ ] **Step 1: Extract `applyParsedPairing` and add `applyDeepLinkPairing` in the ViewModel**

  In `app/src/main/java/com/urlxl/mail/push/PushHomeViewModel.kt`, replace the existing `pairFromLink` function (currently `PushHomeViewModel.kt:65-101`):

  ```kotlin
      fun pairFromLink(link: String) {
          scope.launch {
              isWorking.value = true
              val parsed = NativePairingDeepLinkParser.parse(link)
              when (parsed) {
                  is PairingParseResult.Error -> {
                      localMessage.value = parsed.reason
                      isWorking.value = false
                  }
                  is PairingParseResult.Success -> {
                      val resolution = NativeRegistrationEndpointResolver.resolve(
                          qrReg = parsed.pairing.registrationUrl.takeIf { it.isNotBlank() },
                          qrServerUrl = parsed.pairing.serverUrl,
                      )
                      when (resolution) {
                          is NativeRegistrationEndpointResolver.Resolution.MissingServerUrl -> {
                              localMessage.value = "Pairing QR is missing a server URL"
                              isWorking.value = false
                          }
                          is NativeRegistrationEndpointResolver.Resolution.Resolved -> {
                              val pending = parsed.pairing.copy(registrationUrl = resolution.registrationUrl)
                              val result = graph.syncCoordinator.attemptPairing(pending)
                              if (result is NativeRegistrationResult.Success) {
                                  // If the server put this user in pull mode, start fetching immediately.
                                  graph.pullCoordinator.pullNowAsync()
                              }
                              localMessage.value = when (result) {
                                  is NativeRegistrationResult.Success -> "Device paired and token synced"
                                  is NativeRegistrationResult.Error -> {
                                      val suffix = if (result.expiredPairingToken) " — rescan the pairing QR code" else ""
                                      "Pairing failed: ${result.message}$suffix"
                                  }
                              }
                              isWorking.value = false
                          }
                      }
                  }
              }
          }
      }
  ```

  with this (the `Success` branch's body moves unchanged into `applyParsedPairing`, called from both `pairFromLink` and the new `applyDeepLinkPairing`):

  ```kotlin
      fun pairFromLink(link: String) {
          scope.launch {
              isWorking.value = true
              val parsed = NativePairingDeepLinkParser.parse(link)
              when (parsed) {
                  is PairingParseResult.Error -> {
                      localMessage.value = parsed.reason
                      isWorking.value = false
                  }
                  is PairingParseResult.Success -> applyParsedPairing(parsed.pairing)
              }
          }
      }

      /** Applies a deep-link pairing the user has already confirmed via the
       *  destination-server dialog in PushPairingActivity — unlike [pairFromLink]
       *  (QR scan, itself a deliberate user action), a deep link can fire from any
       *  app with zero user awareness, so it requires that separate confirmation
       *  step before reaching this. */
      fun applyDeepLinkPairing(pairing: PairingData) {
          scope.launch {
              isWorking.value = true
              applyParsedPairing(pairing)
          }
      }

      private suspend fun applyParsedPairing(pairing: PairingData) {
          val resolution = NativeRegistrationEndpointResolver.resolve(
              qrReg = pairing.registrationUrl.takeIf { it.isNotBlank() },
              qrServerUrl = pairing.serverUrl,
          )
          when (resolution) {
              is NativeRegistrationEndpointResolver.Resolution.MissingServerUrl -> {
                  localMessage.value = "Pairing QR is missing a server URL"
                  isWorking.value = false
              }
              is NativeRegistrationEndpointResolver.Resolution.Resolved -> {
                  val pending = pairing.copy(registrationUrl = resolution.registrationUrl)
                  val result = graph.syncCoordinator.attemptPairing(pending)
                  if (result is NativeRegistrationResult.Success) {
                      // If the server put this user in pull mode, start fetching immediately.
                      graph.pullCoordinator.pullNowAsync()
                  }
                  localMessage.value = when (result) {
                      is NativeRegistrationResult.Success -> "Device paired and token synced"
                      is NativeRegistrationResult.Error -> {
                          val suffix = if (result.expiredPairingToken) " — rescan the pairing QR code" else ""
                          "Pairing failed: ${result.message}$suffix"
                      }
                  }
                  isWorking.value = false
              }
          }
      }
  ```

  `PairingData` is already in the same `com.urlxl.mail.push` package as `PushHomeViewModel` — no new import needed.

- [ ] **Step 2: Add the confirmation dialog strings**

  In `app/src/main/res/values/strings.xml`, add these three lines directly after the existing `push_pairing_use_firebase` line (currently `strings.xml:91`):

  ```xml
      <string name="push_pairing_use_firebase">Use Firebase</string>
      <string name="pairing_confirm_title">Pair this device?</string>
      <string name="pairing_confirm_message">This link wants to pair this device with:\n\n%1$s\n\nOnly continue if you trust the source of this link.</string>
      <string name="pairing_confirm_positive">Pair</string>
  ```

- [ ] **Step 3: Add the confirmation dialog in the Activity**

  In `app/src/main/java/com/urlxl/mail/push/PushPairingActivity.kt`, add the `AlertDialog` import. The import block currently starts (`PushPairingActivity.kt:3-13`):

  ```kotlin
  import android.Manifest
  import android.content.pm.PackageManager
  import android.os.Bundle
  import android.view.LayoutInflater
  import android.view.View
  import android.view.ViewGroup
  import android.widget.BaseAdapter
  import android.widget.Button
  import android.widget.ListView
  import android.widget.TextView
  import android.widget.Toast
  import androidx.activity.result.contract.ActivityResultContracts
  ```

  Add one import so it reads:

  ```kotlin
  import android.Manifest
  import android.content.pm.PackageManager
  import android.os.Bundle
  import android.view.LayoutInflater
  import android.view.View
  import android.view.ViewGroup
  import android.widget.BaseAdapter
  import android.widget.Button
  import android.widget.ListView
  import android.widget.TextView
  import android.widget.Toast
  import androidx.activity.result.contract.ActivityResultContracts
  import androidx.appcompat.app.AlertDialog
  ```

  Then replace the existing `consumeDeepLink` function (currently `PushPairingActivity.kt:193-196`):

  ```kotlin
      private fun consumeDeepLink(intent: android.content.Intent?) {
          val data = intent?.dataString ?: return
          viewModel.pairFromLink(data)
      }
  ```

  with this (parses first, shows a confirmation dialog with the destination server URL, and only applies on explicit confirm):

  ```kotlin
      private fun consumeDeepLink(intent: android.content.Intent?) {
          val data = intent?.dataString ?: return
          when (val parsed = NativePairingDeepLinkParser.parse(data)) {
              is PairingParseResult.Error -> Toast.makeText(this, parsed.reason, Toast.LENGTH_SHORT).show()
              is PairingParseResult.Success -> confirmAndApplyDeepLinkPairing(parsed.pairing)
          }
      }

      private fun confirmAndApplyDeepLinkPairing(pairing: PairingData) {
          AlertDialog.Builder(this)
              .setTitle(R.string.pairing_confirm_title)
              .setMessage(getString(R.string.pairing_confirm_message, pairing.serverUrl))
              .setPositiveButton(R.string.pairing_confirm_positive) { _, _ -> viewModel.applyDeepLinkPairing(pairing) }
              .setNegativeButton(R.string.cancel, null)
              .show()
      }
  ```

  `NativePairingDeepLinkParser`, `PairingParseResult`, and `PairingData` are already in the same `com.urlxl.mail.push` package as `PushPairingActivity` — no new imports needed for those. `Toast` is already imported (`PushPairingActivity.kt:12`).

- [ ] **Step 4: Build**

  Run: `./gradlew assembleDebug`
  Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Manually verify on a device/emulator**

  No automated test covers this flow. Install and check by hand:
  - Trigger the deep link:
    ```bash
    adb shell am start -a android.intent.action.VIEW \
      -d "llamalabels://native-pair?sub=x&hash=y&srv=https://example.com&pt=z"
    ```
    Confirm a dialog appears titled "Pair this device?" showing `https://example.com` in its message, before anything is applied.
  - Tap **Cancel** — confirm the existing pairing (if any) is untouched (check `PushPairingActivity`'s status/subscriber display, or `SecurePairingStore` state, is unchanged).
  - Trigger the deep link again and tap **Pair** — confirm it applies exactly as pairing did before this change (same `attemptPairing` flow, same success/error messages).
  - Scan a real pairing QR code (`btnScanQr` → `scanQr()`) — confirm it still applies immediately with **no** new dialog (QR path unaffected).

- [ ] **Step 6: Commit**

  ```bash
  git add app/src/main/java/com/urlxl/mail/push/PushHomeViewModel.kt app/src/main/java/com/urlxl/mail/push/PushPairingActivity.kt app/src/main/res/values/strings.xml
  git commit -m "fix: require confirmation before applying a deep-link pairing"
  ```
