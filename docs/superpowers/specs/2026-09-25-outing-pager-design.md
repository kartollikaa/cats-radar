# Outing pager — design

- **Date:** 2026-09-25
- **Status:** approved by the owner in chat, 2026-09-25
- **Decomposition:** [docs/tbd/decompositions/2026-09-25-outing-pager.md](../../tbd/decompositions/2026-09-25-outing-pager.md)
- **Builds on:** [2026-09-21-cats-radar-design.md](./2026-09-21-cats-radar-design.md) §3.5;
  [2026-09-25-many-photos-per-cat-design.md](./2026-09-25-many-photos-per-cat-design.md) (its detail
  screen's photo pager is nested in each cat page)

## What the owner asked for

The encounter detail screen pages through the cats of the cat's outing with an ordinary horizontal swipe.
Swiping on past the first or the last cat stretches the screen; released past a threshold it moves to the
neighbouring outing, released short of it the screen springs back and stays.

Owner decisions, 2026-09-25:

- **Pages run like the Encounters list**, newest to oldest. Swiping towards the next page shows an older cat;
  stretching past the oldest cat moves to the older outing, past the newest to the newer one.
- **A jump replaces in place.** The same screen slides to the target outing; the back stack never grows, and
  Back returns to wherever the detail was opened from.
- **Everywhere the detail opens** — the Encounters list, a Map dot, a Map spot sheet, a Places area — the cat
  pages through its own outing.
- **While stretching:** the page gives way, the target outing's label ("Yesterday, 18:40 · 4 cats") fades in
  in the gap, a haptic tick marks the threshold and the label fills while armed.
- **A delete never splits the outing on screen.** Globally the rule stays as it is: outings are derived from
  live cats, so deleting a stray tap that glued two walks separates them again, and statistics, the map line
  and location backfill stay right. Changing the rule globally was offered and rejected: the data cannot tell
  a genuine middle cat from a stray tap between two walks, a stray tap could then never be undone out of an
  outing, the purge would split it silently a month later, and a backup carries no deleted rows.
- **A cat's photos keep their own pager inside its page.** A cover with a count, leaving the paging to the
  viewer, was chosen first; the photo pager shipped with many photos per cat before that amendment landed, and
  the owner kept it (2026-09-26). The cats pager nests it (*The photo pager inside a page*, below).

## The outing window (`:domain`)

A pure function in `dev.catsradar.domain.session`, beside `SessionSplitter`:

```kotlin
data class OutingWindow(
    val cats: List<Encounter>,        // the pages, newest first
    val newer: List<Encounter>?,      // the adjacent newer outing, oldest first; null at the newest end
    val older: List<Encounter>?,      // the adjacent older outing, oldest first; null at the oldest end
)

fun outingWindow(encounters: List<Encounter>, shown: Set<String>): OutingWindow?
```

- The live cats are grouped by `SessionSplitter.groupByOuting()`, unchanged.
- The window is the contiguous run of outings from the oldest to the newest one that holds a cat in `shown`;
  `cats` is every live cat in that run. Deleted cats are never in it.
- `null` when no cat in `shown` is live.
- `newer` and `older` are the outings just outside the run.

**Why a set, not one cat.** The Store passes the cats currently on its pages. An outing that a delete split
in two still holds a shown cat in each half, so both stay; a cat logged into the outing, or an import that
merges the next outing into it, joins it; a cat only ever leaves the pages by being deleted. On opening and
after a jump the set is the outing of one cat, so the window is exactly that outing.

**Landing after a jump:** the newest cat of an older outing, the oldest cat of a newer one — the cat nearest
to where the swipe came from.

## The Store

`EncounterDetailStore(openedId, restoredId, …)` keeps two things of its own: the **anchor** (the cat on
screen) and the **shown** set. It collects `ObserveEncounters()` and on each emission calls `outingWindow`;
the mapper turns the window into state. Reading the whole table is unavoidable while outings are derived; the
list and the map already hold the same query open, and the window is `distinctUntilChanged` so the mapper runs
only when this outing or its neighbours change.

After each emission `shown` becomes the window's cats. Start: the anchor is `restoredId` when that cat is live,
else `openedId`, else the screen is *Missing*, and `shown` is the anchor alone. Only the anchor survives the
process dying, so a restored screen reopens on its outing as derived then.

```kotlin
data class Loaded(
    val pages: ImmutableList<CatPage>,     // per cat: id and today's detail labels
    val currentId: String,                 // the anchor; the pager follows it
    val currentNumber: Int,                // 1-based, for "2 / 5"
    val newer: NeighbourOuting?,
    val older: NeighbourOuting?,
    val jump: Int,                         // counts jumps; the slide animation keys on it
    val jumpDirection: OutingDirection?,
    val undoVisible: Boolean,
)

data class NeighbourOuting(val label: String, val count: Int)   // label: the Encounters header, day and time
enum class OutingDirection { NEWER, OLDER }
```

`Deleted(undoVisible)` and `Missing` stay; `Deleted` is now only the last cat's. Counts are `Int`s rendered
through plurals; the position and neighbour words live in EN/RU resources. The Encounters list's outing header
is extracted so both screens build the label with one function.

### Intents and effects name their cat

Every per-cat intent and effect carries the cat's id: `CoatPicked(catId, coat)`, `TakePhotoClicked(catId)`,
`PickPhotoClicked(catId)`, `PhotoTaken(catId, uri)`, `PhotosPicked(catId, uris)`, `PhotoClicked(catId, photoId)`,
`CoordinatesClicked(catId)`; `OpenCamera(catId)`, `OpenPhotoPicker(catId)`, `OpenPhoto(catId, photoId)`,
`OpenMap(catId)`. The nav host opens the viewer and the map on that cat, never on `key.id`.

**A photo lands on the cat it was taken for.** The camera launcher's saved state holds each target with its
cat's id, and the picker saves the id it was opened for, so a result delivered after the process died still
names its cat. A photo on a cat cannot be removed, so a guess would be permanent. One *waiting for the camera
or picker* flag covers the screen: a tap on another page's buttons while one is open opens nothing.

Attaching is tracked per cat. Swiping to another page is not leaving the screen: the attempt carries on and
its cat's buttons stay disabled until its photo arrives.

### Paging and jumping

- `PageSettled(catId)` moves the anchor to a cat on the pages; an id no longer on them is ignored.
- `OutingEdgeReleased(direction)` with a neighbour there: `shown` becomes that outing, the anchor its landing
  cat, `jump` counts up and `jumpDirection` is set. Without one it does nothing. TalkBack's *Newer outing* and
  *Older outing* actions send the same intent.

State is the only writer of the anchor. The pager scrolls to `currentId` whenever it changes, and reports a
settled page only when that page's cat differs from it.

### Deleting

- **A cat with other pages beside it** leaves the pager at once. The anchor moves to the next older page, or
  the newer one when it was the oldest. An undo bar shows for `Tuning.UNDO_VISIBLE`; Undo restores the cat,
  puts it back among the pages and shows it. A second delete inside the window replaces the undo and the first
  stands, as the Encounters list does.
- **The last cat on the pages** shows today's *removed* state with Undo; when the window closes the screen
  closes, once.
- **A failed delete** puts the cat and the anchor back. **A failed undo** reopens its window, unless a newer
  delete owns the bar by then; that one stays the one to undo.
- **Delete tapped twice** writes once.
- **A cat deleted elsewhere** leaves the pages the same way, without an undo bar; the anchor moves as above.
  With no page left the screen is *Missing*. A cat brought back elsewhere — an undo, a backup restoring it —
  rejoins the pages when it falls inside their outings.

## The screen

- The back arrow stays at the start of the app bar; its centre shows "2 / 5" while the pages hold more than
  one cat, read by TalkBack as "Cat 2 of 5".
- A `HorizontalPager` keyed by cat id, with its own overscroll turned off; a cat logged while watching never
  shifts the page on screen.
- Around it, an `AnimatedContent` keyed by `jump` slides the whole pager in `jumpDirection`. The outgoing pager
  is not scrollable and reports nothing. A key made from the outing's cats would replay the slide whenever its
  oldest cat is deleted, so the key is the counter.

### The pull

A `NestedScrollConnection` on a wrapper around the pager, not an `OverscrollEffect`: the pager never calls
its overscroll effect when it has one page, and a drag that starts inside a nested horizontal scroller — the
coat row and the photo pager on every page — bypasses it.

- Horizontal scroll the pager leaves over from a user drag accumulates as a resisted pull; pre-scroll in the
  opposite direction takes the pull back first.
- The page translates away from the pulled edge and stretches slightly, anchored at the far edge; the
  neighbour's label, drawn beside the pager, fades in with the pull in the gap that opens.
- Past a fixed pull distance the pull is armed: the label fills and `HapticFeedbackType.GestureThresholdActivate`
  ticks, once per crossing. Pulling back below it disarms, silently.
- Release while armed sends `OutingEdgeReleased`; otherwise the pull springs back. At the newest or the oldest
  outing there is no label and no threshold, only the give.
- **A cancelled gesture never jumps.** When the system takes a swipe from the screen edge for predictive back
  the app gets a cancel, which reaches the scroll chain like a slow release; the pull tells the two apart and
  springs back.
- Directions follow the pager, so right-to-left layouts mirror them.
- The pull distance and the haptic are gesture state; they never reach the Store.

### The photo pager inside a page

A cat with several photos shows them in their own horizontal pager at the top of its page, as many photos per
cat built it. The two pagers share one axis:

- A horizontal drag that starts on a photo moves the photos first. Past the cat's first or last photo, the rest
  of the drag moves the cats pager, and past the first or last cat it feeds the pull, so a stretch to the next
  outing can start on a photo.
- A fling off the last photo does not carry its speed into the cats pager, which settles by position: a short
  flick there can come back to the same cat. Accepted with the photo pager.
- The photo's own position stays over the photo; the cat's "2 / 5" stays in the bar.
- Each cat keeps its photo position while the user swipes to other cats and back.

## Not built

Returning the Encounters list to the cat last shown, paging within a Places area or a map spot rather than an
outing, pinch or long-press gestures on a page, a split gesture for outings.

## Tests

- **Domain** (`OutingWindowTest`): pages newest first; neighbours at both ends and none at the ends of history;
  a split outing keeps both halves while a shown cat is in each; a cat logged into the outing joins; a merge by
  import joins; deleted cats never appear; no live shown cat is `null`; landing cats per direction.
- **Store** (`EncounterDetailStoreTest`, rewritten around ids): start from the restored id, the opened one, or
  *Missing*; page settling; a jump and a jump with no neighbour; a delete with neighbours on either side; the
  last cat's delete, with and without undo; a failed delete; a failed undo with and without a newer window;
  undo shows the restored cat; deleted elsewhere, anchor and whole window; a camera and a picker result after
  recreation reaching their own cat; one camera across pages; attaching carries on across a swipe; `newer`
  appearing after a tally; no jump when the oldest cat is deleted.
- **Screen** (Robolectric Compose): an armed release jumps once; a short release and a cancelled gesture do
  not; no threshold at the ends of history; a one-cat outing still pulls and jumps; a pull that starts on the
  coat row, and one that starts on a photo; a drag past a cat's last photo moving to the next cat; one haptic per
  crossing; the position and its description; accessibility actions present only where a neighbour is; the
  pager follows `currentId`.
- **Entry** (`:app`): the viewer and the map open the cat on screen after a swipe; a restored entry reopens the
  cat that was on screen.
