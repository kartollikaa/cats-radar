# Slice P3b — The detail screen pages through its outing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The encounter detail screen shows its outing's cats in a `HorizontalPager`, newest first, with "2 / 5"
in the bar; a settled swipe moves the cat on screen, and a restored screen reopens on the cat it was showing.

**Architecture:** `:ui` — `EncounterDetailScreen` draws `Loaded.pages` in a `HorizontalPager` keyed by cat id
(its own overscroll off), starting on and following `currentId`, and calls `onPageSettled(catId)` only when the
settled page's cat differs from `currentId`; the bar's centre shows the position. `:presentation` —
`PageSettled(catId)` moves the cat on screen through `OutingPages.settle`. `:app` — the destination dispatches it,
saves `currentId` with `rememberSaveable`, and passes it as `restoredId`.

**Tech Stack:** Compose foundation pager, Robolectric + Compose UI test, Koin, turbine.

**Spec:** `docs/superpowers/specs/2026-09-25-outing-pager-design.md` § The screen, § Paging and jumping
(`PageSettled`), § The photo pager inside a page; map `docs/tbd/decompositions/2026-09-25-outing-pager.md` row P3b.
Criteria: `outing-pager-p3b.md` in the acceptance directory (Task 0).

## Global Constraints

- **Stacked on P3a-2** (#204, branch `tech/detail-store-serves-outing`); this PR's base is that branch until #204
  merges, then `main`.
- **Out of scope:** the undo bar and the delete rules (P4: a delete still shows the removed state and closes);
  neighbours, jumps, `OutingEdgeReleased`, TalkBack's outing actions (P5a); the pull past the edge (P5b).
- **Compose** (`docs/rules/compose-patterns.md`): one lambda per action with a `{}` default; `modifier` first
  optional; strings from resources (EN and RU); "Pass callbacks down unchanged. Wrap only at the level that owns
  data the child doesn't know (a pager adding its `page`)".
- **MVI:** state is the only writer of the cat on screen; the screen reports, the Store decides.
- **detekt:** `EncounterDetailStore` stays at 10 functions (the `PageSettled` branch calls `OutingPages.settle` and
  the existing `refresh`).
- **Tests first; every commit compiles every module; docs in the same PR.**

## Decisions

1. **Follow, then report.** `LaunchedEffect(currentId)` scrolls the pager to the cat on screen when the state moves
   it (a hand-over, a restore); a `snapshotFlow` of `settledPage` reports a settled cat only when it differs from
   `currentId`, so the report never echoes the state back.
2. **Keys keep the page.** The pager is keyed by cat id, so a cat logged while watching (a new page before the one
   on screen) leaves the cat on screen where it is, and nothing is reported.
3. **The photo pager nests as it is.** Each page keeps `DetailPhotoPager`; the lazy pager saves each page's
   `rememberSaveable` state (photo position, scroll) while it is off screen.
4. **The anchor survives the process through the destination**: `rememberSaveable` holds the last `currentId`,
   passed to Koin as `restoredId`; the ViewModel keeps it across configuration changes on its own.
5. **The position lives at the bar's centre** (`BackBar` gains `center`, which `CenterAppBar` does not mark as a
   heading), text "2 / 5", read "Cat 2 of 5"; shown while there is more than one page.

---

### Task 0: Freeze the acceptance criteria — `acceptance:acceptance-criteria` (auto) into `outing-pager-p3b.md`.

### Task 1: `PageSettled` in the Store

**Files:** `EncounterDetailIntent.kt` (+`PageSettled(catId)`), `EncounterDetailStore.kt`, `OutingPages.kt`
(`ShownPages.settledOn`), `EncounterDetailStoreTest.kt`.

- [ ] Test first — `EncounterDetailStoreTest`:
  *settling on another page puts that cat on screen*: pages `[OTHER, ID]`, `PageSettled(OTHER)` →
  `currentId == OTHER`, `currentNumber == 1`;
  *a settled id off the pages is ignored*: `PageSettled("elsewhere")` → state unchanged;
  *after settling, a delete removes the settled cat*: `PageSettled(OTHER)`, `DeleteClicked` →
  `softDeletedIds == [OTHER]`.
- [ ] Implement: `ShownPages.settledOn(catId)` (copy with `currentId` when the cat is in the window, else itself);
  `OutingPages.settle` uses it; the Store's branch is `pages.settle(id); shown = shown?.settledOn(id); refresh()`.
- [ ] `:presentation` tests and detekt green; commit.

### Task 2: The pager and the position

**Files:** `ui/…/detail/EncounterDetailScreen.kt` (the pager, `DetailPagesTestTag`, `onPageSettled`),
`ui/…/components/BackBar.kt` (`center`), `ui/src/main/res/values{,-ru}/strings.xml` (`detail_position`,
`detail_position_description`), `app/…/detail/EncounterDetailPagerTest.kt` (new), `DetailStates.kt` (a
several-page builder).

- [ ] Tests first — `EncounterDetailPagerTest` (Robolectric Compose, `loaded(pages, currentId)`):
  *a swipe to the next page reports the older cat, once*;
  *the pager follows the cat on screen when the state moves it*;
  *a cat logged while watching keeps the cat on screen and reports nothing*;
  *several pages show the position of the cat on screen, read as Cat n of m*;
  *a single cat shows no position*;
  *a drag past a cat's last photo moves on to the next cat*.
- [ ] Implement per Decisions 1–3 and 5.
- [ ] `:app:testDebugUnitTest --rerun`, `:ui:lint`, detekt green; commit.

### Task 3: The destination

**Files:** `app/…/navigation/EncounterDetailDestination.kt`, `app/…/navigation/EncounterDetailEntryTest.kt` (or a new
`EncounterDetailPagerEntryTest.kt`).

- [ ] Tests first (real Koin and Room, two cats of one outing, the opened one newest):
  *after a swipe, the viewer opens on the cat swiped to*; *after a swipe, set on map opens the picker for the cat
  swiped to*; *after a swipe, the coordinates open the map on the cat swiped to*;
  *a restored entry reopens the cat that was on screen* — `StateRestorationTester` with a `ViewModelStore`
  remembered inside the content, so the restore gets a fresh Store and only saved state.
- [ ] Implement Decision 4 and dispatch `PageSettled`.
- [ ] Green; commit.

### Task 4: Docs — `encounter-detail.md` (the pager, position, restore; *Not built yet* loses them), `browsing-cats.md`,
`map.md`, `places.md` (the detail pages through the cat's outing), `photo-viewer.md` (opened for the cat on screen),
`coat.md` (the coat of the page it sits on); the map row P3b; the plan archive of P3a-2 once it ships stays with
that slice.

### Task 5: Verification — `CI=true ./gradlew check :app:assembleRelease`; mutations: **N1** the settled report
without the differs-guard (reports on start) → *a cat logged while watching…*; **N2** no follow effect →
*the pager follows…*; **N3** the pager unkeyed → *a cat logged while watching…*; **N4** the destination passes
`null` again → *a restored entry reopens…*; **N5** `PageSettled` ignored → *settling on another page…*; `/code-review`
on the PR; the acceptance gate.

## Size

Estimated ~450 (pager and bar ~90, Store ~15, destination ~10, strings 4; tests ~300). Measured ~740 reviewable
lines: the screen file reached detekt's function limit, so the page content moved into `CatPager.kt` (~130 lines of
move), and the tests came to ~400. Over the 600 target, under the 1,000 cap; recorded in the map's decision log.
