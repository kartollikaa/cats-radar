# Slice P1 — The outing window in the domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `outingWindow(encounters, shown)` in `:domain` returns the cats of every outing from the oldest to the newest one holding a shown cat, newest first, plus the outing on either side and the cat a move to each lands on; nothing calls it yet.

**Architecture:** A pure top-level function and its result type beside `SessionSplitter` in `dev.catsradar.domain.session`. It groups with `SessionSplitter.groupByOuting()` — the one place the gap rule lives — and only picks a contiguous range of those outings, so a split outing whose halves are both shown stays whole while the global rule stays derived.

**Tech Stack:** Kotlin Multiplatform (`commonMain`/`commonTest`), `kotlin.test`, kotlinx-datetime `Instant`.

**Spec:** `docs/superpowers/specs/2026-09-25-outing-pager-design.md` § The outing window. Map: `docs/tbd/decompositions/2026-09-25-outing-pager.md` P1. Criteria: `outing-pager-p1.md` in the acceptance directory (Task 0).

## Global Constraints

- `:domain` depends on kotlinx only; no `android.*` / `androidx.*` import (Konsist `ModuleBoundaryTest`).
- The file declares package `dev.catsradar.domain.session`.
- The gap rule is not re-implemented: grouping goes through `SessionSplitter.groupByOuting()`.
- Comments: default none; KDoc only for what the signature does not say (orders, nulls). English. See
  `docs/rules/code-commenting-standards.md` — no UI words ("page", "screen", "swipe") in domain KDoc.
- Tests first in `domain/src/commonTest`, with `encounterFixture` from `dev.catsradar.domain.testing`.
- `./gradlew check` green before the PR; branch `tech/outing-window` (already renamed, not yet pushed).
- The PR also carries the already-committed spec and decomposition map (`11d1e157`).

---

### Task 0: Freeze the acceptance criteria

Run the `acceptance:acceptance-criteria` skill for this slice before any code. Save to
`~/.claude/projects/-Users-dmitrijmaksimov-Projects-Cats Radar--claude-worktrees-firebase-analytics-crashlytics-5db5f8/acceptance/outing-pager-p1.md`.
The criteria cover, each with its evidence: every behaviour test in Task 1 by name; the four mutations in
Task 1 Step 6 each failing a named test; `outings.md` stating the window and a current consumer list;
`./gradlew check` green; no caller of `outingWindow` outside tests.

---

### Task 1: The window, its tests and its doc

**Files:**
- Create: `domain/src/commonMain/kotlin/dev/catsradar/domain/session/OutingWindow.kt`
- Test: `domain/src/commonTest/kotlin/dev/catsradar/domain/session/OutingWindowTest.kt`
- Modify: `docs/features/outings.md`

**Interfaces:**
- Consumes: `SessionSplitter.groupByOuting(encounters: List<Encounter>, gap: Duration = Tuning.SESSION_GAP): List<List<Encounter>>` — live cats only, each outing oldest first, outings oldest first.
- Produces (P3 and P5a rely on these names):
  ```kotlin
  data class OutingWindow(
      val cats: List<Encounter>,       // newest first
      val newer: List<Encounter>?,     // oldest first; null at the newest end
      val older: List<Encounter>?,     // oldest first; null at the oldest end
  ) {
      val newerLanding: Encounter?     // newer?.first()
      val olderLanding: Encounter?     // older?.last()
  }
  fun outingWindow(encounters: List<Encounter>, shown: Set<String>): OutingWindow?
  ```

- [ ] **Step 1: Write the failing tests**

Create `domain/src/commonTest/kotlin/dev/catsradar/domain/session/OutingWindowTest.kt`:

```kotlin
package dev.catsradar.domain.session

import dev.catsradar.domain.model.Encounter
import dev.catsradar.domain.testing.encounterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OutingWindowTest {

    private val e1 = cat("e1", 0.minutes)
    private val e2 = cat("e2", 10.minutes)
    private val m1 = cat("m1", 3.hours)
    private val m2 = cat("m2", 3.hours + 10.minutes)
    private val m3 = cat("m3", 3.hours + 20.minutes)
    private val l1 = cat("l1", 6.hours)
    private val l2 = cat("l2", 6.hours + 10.minutes)
    private val all = listOf(e1, e2, m1, m2, m3, l1, l2)

    @Test
    fun `the window is the outing holding the shown cat, newest first`() {
        assertEquals(listOf(m3, m2, m1), outingWindow(all, setOf("m2"))?.cats)
    }

    @Test
    fun `the outings on either side are its neighbours, oldest first`() {
        val window = outingWindow(all, setOf("m2"))

        assertEquals(listOf(l1, l2), window?.newer)
        assertEquals(listOf(e1, e2), window?.older)
    }

    @Test
    fun `the newest and the oldest outing have nothing beyond them`() {
        assertNull(outingWindow(all, setOf("l1"))?.newer)
        assertEquals(listOf(m1, m2, m3), outingWindow(all, setOf("l1"))?.older)
        assertNull(outingWindow(all, setOf("e2"))?.older)
        assertEquals(
            OutingWindow(cats = listOf(m3, m2, m1), newer = null, older = null),
            outingWindow(listOf(m1, m2, m3), setOf("m1")),
        )
    }

    @Test
    fun `a move lands on the neighbour's cat nearest the window`() {
        val window = outingWindow(all, setOf("m2"))

        assertEquals(l1, window?.newerLanding)
        assertEquals(e2, window?.olderLanding)
        assertNull(outingWindow(all, setOf("l2"))?.newerLanding)
        assertNull(outingWindow(all, setOf("e1"))?.olderLanding)
    }

    @Test
    fun `an outing a delete split in two stays whole while a cat of each half is shown`() {
        val a = cat("a", 0.minutes)
        val gone = cat("gone", 25.minutes).deleted()
        val b = cat("b", 50.minutes)

        assertEquals(
            OutingWindow(cats = listOf(b, a), newer = null, older = null),
            outingWindow(listOf(a, gone, b), setOf("a", "b")),
        )
    }

    @Test
    fun `showing one half of a split outing gives that half, the other beside it`() {
        val a = cat("a", 0.minutes)
        val gone = cat("gone", 25.minutes).deleted()
        val b = cat("b", 50.minutes)

        assertEquals(
            OutingWindow(cats = listOf(a), newer = listOf(b), older = null),
            outingWindow(listOf(a, gone, b), setOf("a")),
        )
    }

    @Test
    fun `a cat logged into the outing joins the window`() {
        val m4 = cat("m4", 3.hours + 40.minutes)

        assertEquals(listOf(m4, m3, m2, m1), outingWindow(all + m4, setOf("m1", "m2", "m3"))?.cats)
    }

    @Test
    fun `an outing a new cat merges into the window joins it`() {
        val n1 = cat("n1", 4.hours + 15.minutes)
        val n2 = cat("n2", 4.hours + 25.minutes)
        val bridge = cat("bridge", 3.hours + 48.minutes)
        val before = outingWindow(all + n1 + n2, setOf("m1", "m2", "m3"))
        val after = outingWindow(all + n1 + n2 + bridge, setOf("m1", "m2", "m3"))

        assertEquals(listOf(n1, n2), before?.newer)
        assertEquals(listOf(n2, n1, bridge, m3, m2, m1), after?.cats)
        assertEquals(listOf(l1, l2), after?.newer)
    }

    @Test
    fun `an outing between two shown ones is on the window`() {
        assertEquals(
            OutingWindow(cats = listOf(l2, l1, m3, m2, m1, e2, e1), newer = null, older = null),
            outingWindow(all, setOf("e1", "l1")),
        )
    }

    @Test
    fun `a deleted cat is never on the window or beside it`() {
        val window = outingWindow(
            listOf(e1, e2.deleted(), m1, m2.deleted(), m3, l1, l2),
            setOf("m1", "m2", "m3"),
        )

        assertEquals(listOf(m3, m1), window?.cats)
        assertEquals(listOf(e1), window?.older)
    }

    @Test
    fun `no live shown cat gives no window`() {
        assertNull(outingWindow(all, emptySet()))
        assertNull(outingWindow(all, setOf("unknown")))
        assertNull(outingWindow(listOf(m1.deleted(), m2), setOf("m1")))
        assertNull(outingWindow(emptyList(), setOf("m1")))
    }

    @Test
    fun `input order does not change the window`() {
        assertEquals(outingWindow(all, setOf("m2")), outingWindow(all.reversed(), setOf("m2")))
    }

    private fun cat(id: String, after: Duration): Encounter = encounterFixture(id, BASE + after)

    private fun Encounter.deleted(): Encounter = copy(deletedAt = occurredAt + 1.hours)

    private companion object {
        val BASE = Instant.parse("2026-09-21T07:00:00Z")
    }
}
```

Timeline behind the fixtures (gap 30 min): outing E 07:00–07:10, M 10:00–10:20, L 13:00–13:10. In the merge
test `bridge` sits 28 min after `m3` and 27 min before `n1`, and `n1` is 55 min after `m3`, so without the
bridge N is its own outing. In the split tests `b` is 50 min after `a`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :domain:testAndroidHostTest --tests 'dev.catsradar.domain.session.OutingWindowTest' --console=plain 2>&1 | tail -30`
Expected: compilation FAIL — `Unresolved reference: outingWindow` / `OutingWindow`.

- [ ] **Step 3: Write the implementation**

Create `domain/src/commonMain/kotlin/dev/catsradar/domain/session/OutingWindow.kt`:

```kotlin
package dev.catsradar.domain.session

import dev.catsradar.domain.model.Encounter

data class OutingWindow(
    /** Every live cat from the oldest to the newest outing holding a shown cat, newest first. */
    val cats: List<Encounter>,
    /** The next newer outing, oldest first; null when the window reaches the newest. */
    val newer: List<Encounter>?,
    /** The next older outing, oldest first; null when the window reaches the oldest. */
    val older: List<Encounter>?,
) {
    val newerLanding: Encounter? get() = newer?.first()

    val olderLanding: Encounter? get() = older?.last()
}

/** Null when no cat in [shown] is live. */
fun outingWindow(encounters: List<Encounter>, shown: Set<String>): OutingWindow? {
    val outings = SessionSplitter.groupByOuting(encounters)
    val holding = outings.indices.filter { index -> outings[index].any { it.id in shown } }
    if (holding.isEmpty()) return null
    val oldest = holding.first()
    val newest = holding.last()
    return OutingWindow(
        cats = outings.subList(oldest, newest + 1).flatten().asReversed(),
        newer = outings.getOrNull(newest + 1),
        older = outings.getOrNull(oldest - 1),
    )
}
```

The landing properties carry no KDoc: their names and the two list KDocs above already say which end is which.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :domain:testAndroidHostTest --tests 'dev.catsradar.domain.session.OutingWindowTest' --rerun --console=plain 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL. Confirm the count from the JUnit XML, not the console:
`grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' domain/build/test-results/testAndroidHostTest/TEST-dev.catsradar.domain.session.OutingWindowTest.xml`
Expected: `tests="12" skipped="0" failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```bash
git add domain/src/commonMain/kotlin/dev/catsradar/domain/session/OutingWindow.kt domain/src/commonTest/kotlin/dev/catsradar/domain/session/OutingWindowTest.kt
git commit -m "The outing window around the cats on show"
```

(End the message with the `Co-Authored-By` trailer from the session's attribution reminder.)

- [ ] **Step 6: Break it on purpose — each mutation must fail a named test**

Apply one mutation at a time to `OutingWindow.kt`, run the Step 4 command with `--rerun`, record which tests
fail, then restore with `git checkout -- domain/src/commonMain/kotlin/dev/catsradar/domain/session/OutingWindow.kt`
(safe: Step 5 committed the green file).

| # | Mutation | Must fail |
|---|----------|-----------|
| 1 | `cats = holding.flatMap { outings[it] }.asReversed()` (no outing between shown ones) | `an outing between two shown ones is on the window` |
| 2 | swap `newer` and `older` arguments | `the outings on either side are its neighbours, oldest first` |
| 3 | drop `.asReversed()` | `the window is the outing holding the shown cat, newest first` |
| 4 | `newerLanding` → `newer?.last()` | `a move lands on the neighbour's cat nearest the window` |

Record the four results for the acceptance gate. `git status` must be clean afterwards.

- [ ] **Step 7: Update `docs/features/outings.md`**

1. After the first paragraph, add a section:

```markdown
## The window around a set of cats

`outingWindow(encounters, shown)` answers "which outings are these cats in, and what is on either side".
Its `cats` are every live cat from the oldest to the newest outing that holds a cat in `shown`, newest
first — so an outing a delete split in two stays whole for as long as a cat of each half is in `shown`,
while `groupByOuting()` itself keeps splitting it. A cat logged into one of those outings, or one that
merges the next outing into them, is in the window too. `newer` and `older` are the outings just outside
it, oldest first, or null at either end of history; `newerLanding` and `olderLanding` are the cats of
each nearest to the window. No live cat in `shown` means no window (`OutingWindowTest`).
```

2. Replace the sentence in *At the edges* that begins "Because outings are derived and not stored, anything
   that needs…" and its list of consumers so the list names every caller: `AttachLocation`'s backfill
   (`split`), `StatsCalculator` (`groupByOuting`, `split`), the Encounters list's grouping and headers
   (`groupByOuting`), the map's outing focus (`outingOf`, in `MapStateMapper`) and walk tracks
   (`outingOf`, in `ObserveOutingTracks`). Keep the paragraph's point about recomputing from scratch.

3. Replace *Not handled yet* — it still says there is no `StatsCalculator` — with:

```markdown
## Not handled yet

`SESSION_GAP` is a constant, not a setting, as the design spec has it for v1: a default parameter on
`split()` and `groupByOuting()`, read from no settings store. What statistics build on outings — rates,
the best outing, the outing in progress, the Outings count and active time — is in
[statistics.md](./statistics.md).
```

4. In *Where the code lives*, add `domain/src/commonMain/kotlin/dev/catsradar/domain/session/OutingWindow.kt`
   and update the "consumed by" list to match step 2.

Re-read every changed line against `docs/rules/code-commenting-standards.md`'s spirit for docs: no volatile
numbers (the gap stays `SESSION_GAP`, never "30 minutes").

- [ ] **Step 8: Commit**

```bash
git add docs/features/outings.md
git commit -m "Outings doc names the window and every caller"
```

---

### Task 2: Check, review, gate, PR

**Files:** none new.

- [ ] **Step 1: Full check**

Run: `./gradlew check --console=plain 2>&1 | tail -40`
Expected: BUILD SUCCESSFUL (detekt, lint, Konsist, every unit test).

- [ ] **Step 2: No caller outside tests**

Run: `grep -rn "outingWindow(" --include='*.kt' . | grep -v '/build/' | grep -v 'OutingWindow.kt' | grep -v 'commonTest'`
Expected: no output. Positive control first — the same command without the last `grep -v` must list
`OutingWindowTest.kt`.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin tech/outing-window
```

If the push stalls or fails with an HTTP/2 framing error, retry with
`git -c http.version=HTTP/1.1 push -u origin tech/outing-window` and verify with
`git ls-remote origin tech/outing-window`.

Open the PR as a draft with `gh pr create --draft --base main --head tech/outing-window --title "The outing window in the domain"`.
Body: what P1 adds (the function, what it keeps whole, nothing calls it yet), that it carries the outing
pager's design and PR map, and the test and
mutation evidence. End the body with the session's PR attribution line. Update the map's P1 status to
`in-review` in the same branch and push.

- [ ] **Step 4: Review**

Run `/code-review` on the PR; fix every confirmed finding with new commits (never a force-push once a
review exists), then re-run `./gradlew check`.

- [ ] **Step 5: Acceptance gate**

Run the `acceptance:acceptance-gate` skill against `outing-pager-p1.md`. Fix and re-run until every criterion
is PASS with evidence from this round, or report the blocker.

- [ ] **Step 6: Ready**

Mark the PR ready for review. Merging is the owner's call. Once it merges, the map's P1 row becomes `merged`
with a decision-log line, and this plan moves to `docs/superpowers/plans/archive/`.
