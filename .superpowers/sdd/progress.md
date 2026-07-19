# Contact Sync Intro Popup Progress Ledger

**Plan:** docs/superpowers/plans/2026-07-19-contact-sync-intro-popup.md
**Spec:** docs/superpowers/specs/2026-07-19-contact-sync-intro-popup-design.md
**Base commit:** b3fb12e
**Start date:** 2026-07-19

## Tasks

- [x] Task 1: Add the one-time-shown flag to `DeviceContactSyncSettings`
- [x] Task 2: Add intro-dialog strings
- [x] Task 3: Extract `DeviceContactSyncEnabler` and refactor `ContactsListActivity` to use it
- [x] Task 4: Show the intro popup before the first QR scan in `PushPairingActivity`

## Notes

- Prior ledger content (contact-fields-editor plan) removed here — that plan
  completed and its "ready to merge" branch review is preserved in git
  history (`git log -p -- .superpowers/sdd/progress.md`) if needed again.
- Worktree created via `EnterWorktree` (branch `worktree-contact-sync-intro-popup`,
  based on local `main` at `ffb454a`).
- `app/google-services.json` is gitignored and wasn't present in the fresh
  worktree; copied over manually from the primary checkout before running
  the baseline build. Any future resume in a *new* worktree for this plan
  will need the same copy step.
- Baseline `./gradlew :app:testDebugUnitTest` passed clean before Task 1 was
  dispatched.
- No Android device/emulator is attached in this environment (`adb devices`
  empty). Tasks 3 and 4 both call for manual on-device verification per the
  plan; both were substituted with build success + careful diff review by
  the implementer/reviewer instead. **A human needs to do a real on-device
  pass (fresh install, both Contacts-screen toggle and the new pairing-
  screen popup flow) before this branch is considered done**, per the
  plan's own Testing section.
- `EnterWorktree`'s default `baseRef` branched from `origin/main` (`ffb454a`),
  missing the spec/plan commits made locally on `main` this session
  (`3ae3d8a`, `b3fb12e`). Fast-forwarded this worktree's branch onto local
  `main` (`git merge --ff-only main`) before dispatching Task 1 — base commit
  above is post-fast-forward.

## Completed

- Task 1: complete (commit `ac0fb6f`). Review: spec ✅, quality Approved, no
  findings of any severity. Byte-for-byte match to the brief's code block;
  build verification (`compileDebugKotlin`) is the correct verification
  method for this Context-backed SharedPreferences class per Global
  Constraints (no Robolectric in this repo).

- Task 2: complete (commit `f16c852`). Review: spec ✅, quality Approved.
  One Minor, not fixed (plan-mandated, not implementer error): new keys are
  singular `contact_sync_intro_*` vs. the cited convention's plural
  `contacts_device_sync_*` — traces back to the plan/brief text itself, and
  Task 4 already depends on these exact names, so not reworked. Carry to
  final whole-branch review for triage.

- Task 3: complete (commit `1e27b0f`). Review: spec ✅, quality Approved, no
  Critical/Important issues. Reviewer independently confirmed
  `DeviceContactSyncEnabler`'s two methods reproduce the original
  `ContactsListActivity` methods call-for-call, in order, and independently
  re-ran the "are Manifest/PackageManager/ContextCompat still used
  elsewhere" grep against the actual file (not just trusting the report) —
  zero matches, import removals safe. One necessary compiler-forced
  deviation from the brief's literal code (explicit type annotation on
  `contactPermissionLauncher` to resolve a real Kotlin circular-type-
  inference error between it and the new `syncEnabler` property);
  reviewer verified it's compile-time-only and behavior-neutral. On-device
  regression check (brief Step 8) not performed — no device/emulator
  attached in this environment; deferred to a human before finishing the
  branch (see Notes).

- Task 4: complete (commit `4e352e5`). Review: spec ✅, quality Approved, no
  Critical/Important issues. This is the task with the plan's one subtle
  correctness property — `scanQr()` must fire exactly once per popup
  interaction, never from both the positive-button handler and the
  permission-launcher callback in the async-permission path. Reviewer
  traced all four interaction paths (sync-enable, async-enable, Not Now,
  cancel) directly against the diff and confirmed each fires `scanQr()`
  exactly once, with `setHasShownSyncIntro(true)` firing once per path via
  `setOnDismissListener`; also independently re-read
  `DeviceContactSyncEnabler.checkAndEnable()` (Task 3) to confirm its
  `Boolean` return semantics matched what this task's logic assumes. Same
  compiler-forced type-annotation deviation as Task 3 (circular type
  inference between `contactPermissionLauncher` and `syncEnabler`),
  verified necessary/inert against Task 3's identical precedent. On-device
  regression check not performed — no device attached; deferred to a human
  (see Notes). **All 4 coding tasks in this plan are now complete.**

## Final Whole-Branch Review

Ready to merge (code): Yes. Release gated on one outstanding item (see below).
Reviewer (opus) independently re-traced all four `scanQr()` call sites across
the full branch diff (not just trusting the four per-task reviews) and
confirmed the sequencing property holds end-to-end: every path (sync-enable,
async-enable, Not Now, cancel) reaches the scanner exactly once, with
`setOnDismissListener` as the sole writer of `hasShownSyncIntro`. Confirmed
`DeviceContactSyncEnabler`'s `AppCompatActivity` reference is lifecycle-safe
(no leak — same lifecycle as the coroutine scope it launches on), confirmed
`ContactsListActivity`'s menu toggle is behavior-neutral after the Task 3
refactor (`invalidateOptionsMenu()` relocated to the `onEnabled` callback at
the same call point), confirmed the same circular-type-inference compiler
fix applied independently in Tasks 3 and 4 is a consistent, inherent
consequence of the launcher↔enabler mutual-reference pattern, not a design
smell. No Critical or Important code issues.

**Outstanding item (release-gating, not merge-blocking):** no on-device or
emulator verification has been performed anywhere in this branch — no
device was available in this environment for any of the 4 tasks. All builds
pass (`compileDebugKotlin`/`processDebugResources`/`assembleDebug`), but the
plan's own Testing section calls for a real device pass. **Before this
branch is considered done, a human needs to run:** Task 3 Step 8 (Contacts-
screen device-sync toggle regression) and Task 4 Step 6 (fresh-install
pairing-screen popup walkthrough — Enable/grant, Not Now, cancel, and
confirm the popup never reappears after any of those).

Four Minor items surfaced, none requiring code changes: (1) singular
`contact_sync_intro_*` vs. plural `contacts_device_sync_*` string-key
convention — plan-mandated, cosmetic; (2) if the dialog is dismissed by a
config change (e.g. rotation) rather than a button/cancel, the flag is
still marked seen but `scanQr()` never fires that time — user just taps
"Scan QR" again and goes straight to the scanner; benign, no code change
recommended; (3) killing the app mid-permission-prompt after tapping
"Enable" marks the intro seen without enabling sync — acceptable, matches
"shown once per install" semantics, feature still reachable from the
Contacts menu; (4) minor import-ordering nit in `PushPairingActivity.kt`
(cosmetic, no ktlint enforcement in this repo).

**Branch is code-ready for `superpowers:finishing-a-development-branch`,
but the on-device smoke test above should happen first — flagging to the
user rather than proceeding straight to merge.**
