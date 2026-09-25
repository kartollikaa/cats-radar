# Encounter detail

Tapping a row in the Encounters list opens that one cat: the day it was logged (relative — "Today",
"Yesterday" — or a calendar date), the time, where its coordinates came from in words, and the
coordinates themselves when there are any, with the fix's accuracy under them. The screen is pushed
above the list, so the bottom bar still shows Encounters as selected; system back and the tab both
return to the list, never to the Counter root. The same screen opens from a dot on the Map
([map.md](./map.md)) and from a cat in the places drill-down ([places.md](./places.md#browsing-them)),
above the screen it was tapped in. A cat that is on the map has its coordinates drawn in the theme's
primary colour with a map mark beside them, and a tap anywhere in its **Where** section switches to the
Map tab with the view on that cat (see [map.md](./map.md#a-cats-coordinates)). The screen scrolls: a photo and the coat picker
together are taller than most phones, and Delete must never end up below the bottom edge.

A back arrow sits at the top, pinned while the rest scrolls, whether the screen shows the cat, the
"removed" state or *Missing* (`EncounterDetailScreenTest`). It leaves the same way system back does,
once however often it is tapped, and only if the screen is still on top (`EncounterDetailStoreTest`,
*back navigates back once, however often it is tapped*; `EncounterDetailEntryTest`). The bar has no
fill of its own: the list runs edge to edge, under the status bar and under the arrow, which sits in
a tonal circle so it stays readable over whatever passes beneath it. Only the list's content is
inset, so at rest the first line starts below the bar and, scrolled to the end, Delete ends above the
bottom bar (`EncounterDetailScreenTest`, *the list runs under the status bar while its first line
starts below the bar*; *scrolled to the end, delete clears the bottom bar*). The coat
picker opens with the cat's own coat on screen, ringed; how it opens and behaves is in
[coat.md](./coat.md#changing-it-later).

Every label is built in `EncounterDetailStateMapper`; the composable renders strings and resolves
one token (`LocationLabel`) to a resource. An accuracy with no coordinates to qualify is dropped
there too. Coordinates are formatted with a fixed five decimals and a decimal point whatever the
locale — that is how coordinates are conventionally written, so it is a fixed pattern in the mapper
rather than a `DateTimeFormatter` concern. The day is derived from the encounter's **own** UTC
offset, not the device's, so a cat logged abroad stays on the day it was logged
(`EncounterDetailStateMapperTest`, *the day comes from the encounter's own offset*).

## Where it was found

The **Where** section opens with the place the cat was found in, named the way Places files it (see
[places.md](./places.md#browsing-them)): the city, the country under it, and the country's flag
before both. The city is the cat's cell's locality, or its admin area when it has none, and a
country with no name of its own shows its two-letter code (`ObserveEncounterPlaceTest`). A cell that
names a country but no city — the cats Places lists under No city — shows the country alone, in the
city's place (`EncounterDetailStateMapperTest`). A cat with no location, or whose cell is not named
yet, has no place line; the section starts with where its coordinates came from. The line appears
while the screen is open once the cell gets its name (`EncounterDetailStoreTest`, *the cat's place
reaches the screen once its cell is named*). TalkBack reads the city and the country with the rest of
the section and skips the flag, which would only repeat the country (`EncounterDetailScreenTest`).

The names are the cat's own cell's. Places names a country after the first of its cats whose cell
has a name for it, so the two differ only when cells of one country were named differently — in
another language, say.

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

A cat with a photo of its own shows the app's copy (see [photos.md](./photos.md#seeing-one)), and a
tap on it opens the photo fullscreen (see [photo-viewer.md](./photo-viewer.md)); one
without shows a **Photo** section instead, with *Take a photo* and *Choose from gallery* — the system
camera, or the system picker for a single image. A second tap before the camera or the picker
answers opens nothing, so a double tap never opens two cameras (`EncounterDetailStoreTest`, *a
second tap before the camera answers opens nothing*). Once the camera or the picker hands a photo
back, the attempt starts: both buttons disable and a progress bar shows under them, so a tap in the
meantime opens nothing (`EncounterDetailStoreTest`, *taking a photo while one is being attached
opens nothing*).

A successful attempt never brings the buttons back: the progress bar stays until the observed row
emits the new `photoPath`, and that emission replaces the section with the photo. Redrawing on
`AttachPhoto`'s result instead would redraw from the last row seen, which has no photo yet, and
flash the offer back for a moment (`EncounterDetailStoreTest`, *a successful attach stays in
progress until the photo arrives, never offering again*).

A cancelled camera or a dismissed picker leaves the screen exactly as it was — no attempt starts
(`EncounterDetailStoreTest`, *a cancelled camera or picker changes nothing*). A photo the camera
hands back is a temporary file, and `EncounterDetailStore` asks for it to be discarded once its
attempt ends, attached or not (*a photo from the camera lands on the cat and its original is
discarded*; *an unreadable photo says so and the offer comes back*). Leaving the screen mid-attempt
cancels the Store before it asks, so that file waits for the start-up cleanup (see
[photos.md](./photos.md), *A camera whose answer never comes*). A photo picked from the gallery was
never copied anywhere first, so there is nothing of the picker's to remove (*a photo from the
gallery lands on the cat and nothing is discarded*). See [photos.md](./photos.md) for what the
attempt itself does with the files, the gallery setting, and an image it cannot decode.

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
- **Leaving during the window** — system back, the back arrow or a tab tap during the undo window
  closes the screen and takes the undo with it; the deletion stands, and the window closing later
  navigates nowhere (*back during the undo window navigates back once, and the window closing adds
  nothing*). That is the defined behaviour, not an accident:
  the window is bound to the screen, and the Counter offers the same trade.
- **Pushing the same detail twice** — a double-tap on a row — puts one entry on the back stack, not
  two (`BottomNavigationTest`, *pushing a key already on the stack leaves the stack unchanged*).
- **The cat is deleted, or given a photo some other way, while an attempt is running** — the screen
  shows no message of its own, since it already shows what the cat became: the "removed" state,
  *Missing*, or the photo it was given first, with this one kept after it (see [photos.md](./photos.md)
  for the attempt's files).
- **Setting the coat while a photo is being attached** keeps both (see
  [coat.md](./coat.md#at-the-edges)).
- **Coordinates that name no place on Earth** — past a pole or the 180th meridian — are still shown
  as numbers, but the map does not draw that cat, so its **Where** section opens nothing
  (`EncounterDetailStoreTest`, *a cat that is not on the map opens no map*). A cat with no
  coordinates has nothing to open either.

## Where the code lives

- `domain/…/usecase/ObserveEncounter.kt`, `ObserveEncounterPlace.kt` (with `RegionTree.placeOf`),
  `DeleteEncounter.kt`, `UndoDelete.kt`
- `presentation/…/detail/` — `EncounterDetailState`, `Intent`, `Effect`, `StateMapper`, `Store`
- `ui/…/detail/EncounterDetailScreen.kt`, `AddPhotoCard.kt`; `ui/…/components/BackBar.kt` — the bar,
  `Flag.kt` — a flag TalkBack skips
- `app/…/navigation/EncounterDetail.kt` (the key), `BottomNavBackStack.push()`,
  `EncounterDetailDestination.kt` (the destination composable, wired into `CatsRadarNavHost.kt`, which
  pushes `PhotoViewer` on the photo's tap, and on the coordinates' tap hands the cat to
  `MapFocusRequest` and selects the Map tab), `PhotoLaunchers.kt` (the camera and gallery-picker
  launchers)

## Not built yet

The coordinates are shown as numbers, and the map is a tap away rather than drawn on this screen. A photo already on a cat cannot be replaced or removed from this screen (see `photos.md`).
