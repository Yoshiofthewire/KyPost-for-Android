# Contact Fields Editor Progress Ledger

**Plan:** docs/superpowers/plans/2026-07-18-contact-fields-editor.md
**Spec:** docs/superpowers/specs/2026-07-18-contact-fields-editor-design.md
**Base commit:** b0db7a1
**Start date:** 2026-07-18

## Tasks

- [x] Task 1: `RepeatableFieldList<T>` generic component
- [x] Task 2: `ExpandableSectionView` collapsible container
- [x] Task 3: Extend `mergedContactDto` for every newly-editable field
- [ ] Task 4: Rewrite `activity_contact_edit.xml` with all sections
- [ ] Task 5: Wire Name section + read-only `isSelf`/`pgpKey` badges
- [ ] Task 6: Wire Work section
- [ ] Task 7: Wire Contact section (full emails/phones lists)
- [ ] Task 8: Wire Addresses section
- [ ] Task 9: Wire Online section (websites + IMs)
- [ ] Task 10: Wire Personal section (birthday, events, relations)
- [ ] Task 11: Wire Notes relocation + Other section (custom fields)
- [ ] Task 12: Manual on-device verification

## Notes

- Prior ledger content (contact-self-flag plan) removed here — that plan is
  complete and merged to `main` (see `git log` for `worktree-contact-self-flag`
  if that history is needed again).
- This worktree's branch was initially created from stale `origin/main` (the
  `EnterWorktree` default `baseRef`), then hard-reset to local `main` after
  discovering it was missing same-session work. Before that reset, three
  bug fixes made earlier in this session (contact-sync mutex, pairing-store
  crash recovery, ContactEditActivity data-loss fix) were still sitting
  *uncommitted* in the primary checkout — committed there as
  `e8bc116`/`e496af3`/`b0db7a1` before this worktree was re-synced. Recorded
  here so a future resume doesn't mistake those for part of this plan's task
  commits.
- The first Task 1 implementer subagent committed to `main` in the primary
  checkout instead of this worktree (same class of mistake the old,
  superseded ledger already warned about once). Caught immediately: reset
  `main` back to `b0db7a1` (safe — the commit `d0cb70c` stayed reachable via
  this worktree's branch, so nothing was lost), no re-implementation needed.
  Flagging again here since it's now happened on two separate plans in this
  repo — worth a harder fix (e.g. an explicit cwd check in the implementer
  prompt) if it recurs a third time.
- Task 1's reviewer found a real stale-closure data-loss bug in the plan's
  own example `bind` wiring (verbatim in the brief, not an implementer
  deviation) that would have propagated into Tasks 7/9/10/11 unchanged.
  Fixed in the plan directly (commit `e7d5e68`) before dispatching any of
  those tasks — see plan diff for the shared-`emit`-lambda pattern now used
  everywhere a row has more than one editable field.

## Completed

- Task 1: complete (commits b0db7a1..e60a065 — `d0cb70c` impl, `e60a065` fix
  for a themed-context test bug the implementer's own device run caught;
  `092a4da` interspersed is controller housekeeping, not part of this task).
  Review: spec ✅, quality Approved. One Important finding (the stale-closure
  bug above) — explicitly scoped by the reviewer as not blocking this task
  (lives in the test's example code, not in `RepeatableFieldList` itself) but
  actioned at the plan level regardless, see Notes above. Minor findings not
  fixed (all explicitly non-blocking, "nice to have"): two unused placeholder
  string resources (`contacts_row_a_hint`/`contacts_row_b_hint`, dead by
  design — real hints are set per-section in later tasks), one test name
  overstating its own coverage (`everyMutation_firesOnChanged`), one
  fully-qualified-type-instead-of-import style nit. Carry these three Minor
  items to the final whole-branch review for triage.

- Task 2: complete (commits cadba8d..782782b — `a486dd3` impl, `782782b` fix
  for the count-badge default-visibility gap the reviewer caught). Review:
  spec ✅, quality Approved, no Critical/Important issues. Reviewer explicitly
  checked for a Task-1-style stale-closure bug and found none (component has
  no text-field bind logic at all). One Minor→acted-on finding: `sectionHeaderCount`
  had no default visibility, so any section that never calls `setItemCount`
  (Name, Work, Notes — none have list fields) would show an empty visible
  badge; fixed via `android:visibility="gone"` default (`782782b`) rather than
  left as debt, since it would have visibly affected every remaining task.
  Other Minor findings not fixed (non-blocking): `setTitle`/`setItemCount`
  have no dedicated test coverage, one redundant cast in the test file, the
  multi-child-order-preservation path in `onFinishInflate` is only exercised
  with a single child. Carry to final whole-branch review for triage.

- Task 3: complete (commit `8aca186`). Review: spec ✅, quality Approved, no
  Critical/Important issues. `mergedContactDto` grew from 8 to 26 params,
  every one correctly threaded signature→`.copy()` with matching names;
  `save()`'s call site correctly left the brief-blessed `null`/`emptyList()`
  placeholders for the 18 fields Tasks 5-11 wire up one at a time. Minor,
  not fixed: `mergedContactDto`'s KDoc is now half-stale (still describes
  itself as pure field-preservation, doesn't mention it now applies real
  edits too) — worth a touch-up once Tasks 5-11 land, not before.
- Incident (milder than the ledger's earlier one): Task 3's implementer left
  a stray, uncommitted *duplicate* of its own change sitting in the primary
  checkout (`/home/yoshi/git/kypost-android`, not this worktree) on
  `ContactEditActivity.kt`/`ContactEditActivityTest.kt` — content-identical
  to what it correctly committed here as `8aca186`, so nothing was at risk
  of being lost, but it had to be discarded (`git checkout --` in the
  primary checkout) to avoid confusing a future session. No new commit
  landed on `main` this time (unlike the earlier incident), so this is
  narrower, but it's the third time in one plan a subagent has touched the
  wrong checkout in some way — the harder fix noted after the first incident
  (an explicit cwd assertion early in the dispatch prompt) is already in use
  and didn't fully prevent this one; worth raising with the user if a fourth
  instance occurs.
