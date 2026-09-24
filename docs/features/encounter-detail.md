# Encounter detail

Tapping a row in the Encounters list opens that one cat: the day it was logged (relative — "Today",
"Yesterday" — or a calendar date), the time, where its coordinates came from in words, and the
coordinates themselves when there are any, with the fix's accuracy under them. The screen is pushed
above the list, so the bottom bar still shows Encounters as selected; system back and the tab both
return to the list, never to the Counter root. The screen scrolls: a photo and the coat picker
together are taller than most phones, and Delete must never end up below the bottom edge.

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

## Giving a cat a photo

A cat with no photo of its own shows a **Photo** section instead, with *Take a photo* and *Choose
from gallery* — the system camera, or the system picker for a single image. Tapping either starts an
attempt: both buttons disable and a progress bar takes their place, so a second tap cannot start a
second attempt over the first (`EncounterDetailStoreTest`, *taking a photo while one is being
attached opens nothing*).

The buttons do not come back the instant an attempt succeeds. On success the Store deliberately does
nothing with `AttachPhoto`'s own result: the row it has been observing since the screen opened will
emit the new `photoPath` on its own, and only that emission swaps the offer for the photo. Acting on
the write's own result instead would show the photo, or reopen the offer, a moment before either is
really true (`EncounterDetailStoreTest`, *a successful attach stays in progress until the photo
arrives, never offering again*).

A cancelled camera or a dismissed picker leaves the screen exactly as it was — no attempt starts
(`EncounterDetailStoreTest`, *a cancelled camera or picker changes nothing*). A camera capture that
does start has its temporary file removed once the attempt finishes, whatever the outcome
(`PhotoLaunchers.kt`); a photo picked from the gallery was never copied anywhere first, so there is
nothing of the picker's to remove (`EncounterDetailStoreTest`, *a photo from the camera lands on the
cat and its original is discarded*; *a photo from the gallery lands on the cat and nothing is
discarded*). See [photos.md](./photos.md) for what the attempt itself does with the files, the
gallery setting, and an image it cannot decode.

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
- **The cat is deleted, or already given a photo some other way, while an attempt is running** — the
  attempt's own files are removed and the row is left as it is, with no separate message: the screen
  already shows whatever the cat became, the same as any other change made elsewhere while it is
  open (see [photos.md](./photos.md)).
- **Setting the coat while a photo is being attached** keeps both; the coat write only ever touches
  the `coat` column (see [coat.md](./coat.md#at-the-edges)).

## Where the code lives

- `domain/…/usecase/ObserveEncounter.kt`, `DeleteEncounter.kt`, `UndoDelete.kt`
- `presentation/…/detail/` — `EncounterDetailState`, `Intent`, `Effect`, `StateMapper`, `Store`
- `ui/…/detail/EncounterDetailScreen.kt`, `AddPhotoCard.kt`
- `app/…/navigation/EncounterDetail.kt` (the key), `BottomNavBackStack.push()`,
  `EncounterDetailDestination.kt` (the destination composable, wired into `CatsRadarNavHost.kt`),
  `PhotoLaunchers.kt` (the camera and gallery-picker launchers)

## Not built yet

No place name and no map yet — coordinates are shown as numbers only. A photo already on a cat
cannot be replaced or removed from this screen (see `photos.md`).
