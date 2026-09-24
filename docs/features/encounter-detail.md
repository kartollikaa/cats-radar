# Encounter detail

Tapping a row in the Encounters list opens that one cat: the day it was logged (relative — "Today",
"Yesterday" — or a calendar date), the time, where its coordinates came from in words, and the
coordinates themselves when there are any, with the fix's accuracy under them. The screen is pushed
above the list, so the bottom bar still shows Encounters as selected; system back and the tab both
return to the list, never to the Counter root. The screen scrolls: a photo and the coat picker
together are taller than most phones, and Delete must never end up below the bottom edge. The coat
picker opens with the cat's own coat on screen, ringed; how it opens and behaves is in
[coat.md](./coat.md#changing-it-later).

Every label is built in `EncounterDetailStateMapper`; the composable renders strings and resolves
one token (`LocationLabel`) to a resource. An accuracy with no coordinates to qualify is dropped
there too. Coordinates are formatted with a fixed five decimals and a decimal point whatever the
locale — that is how coordinates are conventionally written, so it is a fixed pattern in the mapper
rather than a `DateTimeFormatter` concern. The day is derived from the encounter's **own** UTC
offset, not the device's, so a cat logged abroad stays on the day it was logged
(`EncounterDetailStateMapperTest`, *the day comes from the encounter's own offset*).

## Delete and undo

Delete is a soft delete: the row gets a `deletedAt` and disappears from every list and count, but
stays in the database until the purge worker removes it. The screen then shows a "removed" state
with an Undo chip for `Tuning.UNDO_VISIBLE`, the same window the Counter's undo uses, so the two
undo gestures in the app behave alike. When the window closes the chip disappears and the screen
navigates back to the list, exactly once. Undo inside the window clears `deletedAt`; the encounter
is live again and the screen returns to showing it.

The undo affordance lives on the detail screen rather than as a snackbar on the list. A snackbar
would need the list's Store to learn about a deletion made by a different screen — cross-Store
communication the MVI rules forbid — or a shell-level snackbar whose window lives in Material's
timing and cannot be tested with virtual time. Keeping the window in `EncounterDetailStore` makes
"undo is available for exactly this long, then the screen closes" a plain unit test.

## At the edges

- **Pressing delete twice soft-deletes once.** The Store flips its own flag before the suspending
  write, so a second tap in flight sees it and no-ops; the DAO's `WHERE deletedAt IS NULL` guard is
  the second line of defence (*pressing delete twice soft-deletes exactly once*).
- **Undo after the window closed is a no-op** — the deletion stands and the screen has already
  asked to close (*undo after the window closed is a no-op*).
- **An id with no live encounter** — never existed, purged, or deleted from somewhere else — shows a
  "no longer here" message. It is not a crash and not an empty card pretending to be a cat (*an id
  nobody has ever seen renders as missing*; *an encounter soft-deleted elsewhere is never presented
  as live*). The distinction between "I deleted it" and "it is gone" is the Store's own flag: a null
  emission right after its own delete is the delete taking effect, any other null is *Missing*.
- **Deleted elsewhere while on screen** — the screen turns to *Missing* as soon as the row's flow
  emits null (*an encounter deleted elsewhere while on screen turns the screen to missing*).
- **The write fails** — the screen goes back to showing the cat rather than a deletion that did not
  happen, and no back-navigation fires (*a failed delete restores the loaded state*). A failed undo
  reopens the window instead of stranding the user on a closed one.
- **Leaving during the window** — system back or a tab tap during the undo window closes the screen
  and takes the undo with it; the deletion stands. That is the defined behaviour, not an accident:
  the window is bound to the screen, and the Counter offers the same trade.
- **Pushing the same detail twice** — a double-tap on a row — puts one entry on the back stack, not
  two (`BottomNavigationTest`, *pushing a key already on the stack leaves the stack unchanged*).

## Where the code lives

- `domain/…/usecase/ObserveEncounter.kt`, `DeleteEncounter.kt`, `UndoDelete.kt`
- `presentation/…/detail/` — `EncounterDetailState`, `Intent`, `Effect`, `StateMapper`, `Store`
- `ui/…/detail/EncounterDetailScreen.kt`
- `app/…/navigation/EncounterDetail.kt` (the key), `BottomNavBackStack.push()`,
  `CatsRadarNavHost.kt` (`EncounterDetailDestination`)

## Not built yet

No place name and no map. The coordinates are shown as numbers only.
