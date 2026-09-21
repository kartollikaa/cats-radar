# Code Commenting Standards

## The problem a comment solves

When you write code you know *why* — you just spent an hour, a spec, and a discussion deciding it.
The next reader has none of that. They see the code and nothing else: no ticket, no chat, no plan, no
memory of the alternative you rejected. Whatever reasoning stays only in your head is lost the moment
you close the editor.

A comment exists to carry that one lost fact across. Nothing else is a reason to write one.

That asymmetry cuts both ways, and this is where comments usually go wrong: **the reason you know
something is not a reason the reader needs to know it.** Fresh from a design discussion, everything
feels worth saying — the trade-off, the alternative, how another class will consume this. To the
reader that's noise about code they're not looking at. Write the durable fact, not the transcript.

**Default: no comment.** Good names and small functions carry the *what*. Write a comment only when
one of the cases below applies, and only after it passes every test.

---

## What earns a comment

1. **A constraint from outside this code** — a platform quirk, a framework behavior, a hardware or
   OS limit, a legal or business rule. Nothing in the file can reveal it.

   It goes **in the file that makes the call** — the adapter, the mapper, the platform
   implementation — never in the interface above it. An interface says what it guarantees; how that
   is obtained belongs to whoever obtains it. When such a fact outlives the feature and needs more
   than two lines, its home is `docs/reference/`, and the code states the guarantee and points there
   **once**, at the single most surprising call site.
   ```kotlin
   // Geocoder.isPresent() is false on devices without Google services, so cells stay UNAVAILABLE.
   // Glance action callbacks may be killed after a few seconds; only the insert runs here.
   ```

2. **An obvious alternative that doesn't work** — the sign on Chesterton's fence, so the next person
   doesn't "simplify" it back to the broken version.
   ```kotlin
   // Insert before requesting location: a tap must never wait on GPS.
   ```

3. **An invariant the compiler can't express** — an ordering requirement, a thread requirement, a
   value that must stay in sync with another one.
   ```kotlin
   // Must equal the geohash precision PlaceCell rows are keyed by.
   ```

4. **A deliberately surprising value or behavior** — a number that looks arbitrary and isn't, a
   branch that looks wrong and isn't.
   ```kotlin
   // A photo older than this without EXIF GPS is historical; it never gets today's location.
   ```

Non-obvious public APIs get KDoc — what a caller must know that the signature doesn't say. A public
declaration whose name already says everything (enum entries, single-expression properties, simple
data classes) gets nothing.

---

## The tests

Every comment must pass all of them. Each one is a question with a real answer — if you're arguing
your way to "yes", it's a no.

| Test | Question | Fails when |
|---|---|---|
| **Delete** | Remove the comment. Would someone now make a *wrong decision*? | They'd only read slightly slower. That's not enough — leave it deleted. |
| **Different words** | Do the comment's key words differ from the identifiers under it? | It reuses the same nouns and verbs. It's a restatement, not information. A comment sits at a *higher* level of abstraction than its code. |
| **Stranger** | Read it as someone who never saw the spec, the chat, or the PR — and who has not read whatever this file depends on. Does it still make sense? | It needs the discussion to parse, points at a spec, plan or thread, or leans on a name only that dependency's source explains. |
| **Locality** | Is the fact checkable in *this* file? | It describes another file's implementation or a caller's behavior. It will rot the moment that file changes, and nothing here will tell you. |
| **Layer** | Does *this* file use the thing the fact is about? | The fact describes a dependency this file never calls. Swap that dependency in your head: if the file survives the swap and the fact dies with it, the fact belongs where the dependency is used. |

The Locality test has one legitimate cross-file form: an **invariant that must hold** ("must stay in
sync with X"). That's a constraint on this code. A **usage forecast** ("X will use this to build Y")
is not — it's a note about code that isn't here.

Locality and case 1 pull against each other on purpose: an outside constraint is by definition not
checkable in the file, which is exactly why it earns a comment. **Layer** is what settles *which*
file may state it — the one that makes the call, not every layer the value passes through.

---

## How comments go wrong

**1. Restatement** — says what the line already says.
```kotlin
// ❌ Use EXIF location if the photo has one, otherwise the current fix
exifLocation ?: currentFix
```
Fails *Different words*. Delete it.

**2. Brainstorm residue** — a trade-off, alternative, or decision from the discussion that produced
the code.
```kotlin
// ❌ We considered SQL aggregates here but decided in-memory is fine for now
```
Fails *Stranger*. Either state the durable constraint or drop it. Rationale that needs paragraphs
belongs in the **PR description**, the design spec under `docs/superpowers/specs/`, or a module
`README.md` — not in the code.

**3. Forward reference** — describes how other code will consume this.
```kotlin
// ❌ Sessions are used by the Statistics screen to compute the rate block
val sessions: ImmutableList<Session>
```
Fails *Locality*. The consumer moves, the comment lies. The same rule bans domain code describing UI
("shows a spinner", "the chip disappears") — domain code is layer-agnostic; its caller can be any
UI, a test, or a worker. Describe what this emits or guarantees, in its own terms.

**4. Transient scope** — anything that describes work in progress rather than the code: slice or
plan ids ("slice 3", "step 2"), "this PR adds…", "now we…", "for now", "as discussed". Fails
*Stranger*. Comments are permanent; a diff is not. A stable issue link is fine when it explains a
durable workaround — never to narrate a change.

**5. Test narration** — labelling the phases of a test, or re-describing the setup.
```kotlin
// ❌ Arrange — two encounters 31 minutes apart
// ❌ Act — split into sessions
```
Fails *Delete* and *Different words*. Blank lines separate Arrange/Act/Assert, and the test name
already states the scenario. A test earns a comment on the same terms as any other code — usually
for a non-obvious fixture constraint, not for its structure.

**6. Borrowed vocabulary** — an interface explained through the machinery underneath it, and one
outside fact restated in every layer it passes through.
```kotlin
// ❌ in an interface that never talks to the OS
/** Returns null when Google services are missing, so check before calling. */
suspend fun resolve(lat: Double, lon: Double): Place?

// ✅ the interface states its guarantee; the quirk sits in the Android implementation
/** Null when no place is known for the point. */
```
Fails *Layer*. Callers of an interface cannot act on how it is fulfilled — and when the same fact is
copied into every layer it passes through, each copy has to be found and fixed when the underlying
thing changes. One fact, one home: the file holding the dependency.

---

## Format

- **One line. Two at most.** Needing more means the naming or structure is wrong, or the explanation
  belongs in the PR description or a README.
- **English, always** — whatever language the spec, the PR description, or the review thread used.
  User-facing strings keep their own language; this is about comments and KDoc only.
- **Present tense, a fact about the world** — "the geocoder may return several addresses for one
  point", not "we added a take(1) so the mapper doesn't crash".
- **Don't repeat a value that lives in code** — `// waits 8 s for a fix` goes stale the moment the
  constant changes. Name the constant instead.
- **Don't justify the code to a reviewer.** A comment defending a choice to whoever reads the diff is
  brainstorm residue with better manners.
- **Never comment out code** — delete it; git remembers.

## Before you finish

Read every comment you added, cold, as someone who has never seen this task and has not read what
this file depends on. Run every test on each. Delete the ones that don't survive — that's the
expected outcome for most of them.
