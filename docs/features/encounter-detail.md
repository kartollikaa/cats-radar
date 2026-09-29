# Encounter detail

Tapping a row in the Encounters list opens that cat, among the other cats of its outing (see
[Paging through the outing](#paging-through-the-outing)). Under its photos the page names the cat by its
coat — "Ginger & white cat", or "A cat" with no coat noted, «котик» in Russian — and a row of facts
follows: the cat's number (see [Its number](#its-number)), then outlined labels for the day it was logged
(relative — "Today", "Yesterday" — or a calendar date), the time, and its place with the flag when it has a
named one. The facts are labels, not buttons; they wrap onto a second line
when they must, and a screen reader hears them as one item, without the flag (`DetailNamesTest`). Further
down, a cat with a location has its **Where you met** card (see [Where it was found](#where-it-was-found)), and a cat
without one has a notice in its place, right under the facts. The screen is pushed
above the list, so the bottom bar still shows Encounters as selected; system back and the tab both
return to the list, never to the Counter root. The same screen opens from a dot on the Map
([map.md](./map.md)) and from a cat in the places drill-down ([places.md](./places.md#browsing-them)),
above the screen it was tapped in. A cat that is on the map has a small map of its spot in its
card (see [Its map](#its-map)) and a filled **Show on the map** pill at the card's foot, and a tap anywhere on the
card opens a map above the screen with the view on that cat, and back returns to the cat (see
[map.md](./map.md#a-cats-coordinates); `EncounterDetailMapLinkTest`); the pill is the
card's visible cue, not a second control (`DetailWhereTest`). A cat with no location has no card: a notice under its
facts, the pin in a round tertiary icon, reads **No location for this cat** and "It was logged without a fix, so it
is not on the map or in Places.", as one item to a screen reader, with a tonal *Set on map* at its end, which opens
the location picker for that cat above the screen, once however often it is tapped (`EncounterDetailEntryTest`).
The notice gives way to the card as soon as the cat has a location, whichever way it came
(`EncounterDetailStateMapperTest`, *only a cat with no location is offered one on a map*).
The picker itself is in [location.md](./location.md#on-a-map). The screen scrolls: a photo and the
coat card together are taller than most phones, and *Remove this cat* must never end up below the bottom edge.

A back arrow sits at the top, pinned while the rest scrolls, whether the screen shows the cat, the
"removed" state or *Missing* (`EncounterDetailScreenTest`). It leaves the same way system back does,
once however often it is tapped, and only if the screen is still on top (`EncounterDetailStoreTest`,
*back navigates back once, however often it is tapped*; `EncounterDetailEntryTest`). The bar has no
fill of its own: the list runs edge to edge, under the status bar and under the arrow, which sits in
a tonal circle so it stays readable over whatever passes beneath it, its edge in line with the
content's (*the back button lines up with the content under it*). At the bar's other end **More**
sits in the same tonal circle and opens a menu for the cat on screen: *Show on the map*, disabled while the
map does not draw the cat, and *Remove this cat*; a removed or missing cat has no More (`DetailNamesTest`).
Only the list's content is inset, so at rest the first line starts below the bar and, scrolled to the end,
*Remove this cat* ends above the bottom bar (`EncounterDetailScreenTest`, *the list runs under the status bar
while its first line starts below the bar*; *scrolled to the end, remove this cat clears the bottom bar*). The coat is
a card that opens the coat sheet for the cat on screen; how the two look and behave is in
[coat.md](./coat.md#changing-it-later).

Every label is built in `EncounterDetailStateMapper`; the composable renders strings and resolves
one token (`LocationLabel`) to a resource. An accuracy with no coordinates to qualify is dropped
there too. Coordinates are formatted with a fixed five decimals and a decimal point whatever the
locale — that is how coordinates are conventionally written, so it is a fixed pattern in the mapper
rather than a `DateTimeFormatter` concern. The day is derived from the encounter's **own** UTC
offset, not the device's, so a cat logged abroad stays on the day it was logged
(`EncounterDetailStateMapperTest`, *the day comes from the encounter's own offset*).

The state holds each cat as a page — a `CatPage` in `Loaded.pages`, beside the id of the cat on screen and
its position — and the mapper builds one page for each cat of the outing window it is handed, with each
page's own place, number and attempt (`EncounterDetailStateMapperTest`, *a window maps to one page per cat,
newest first, and names the cat on screen and its position*; *each page takes its own cat's place and attempt*;
*each page takes its own cat's number, a shot's page the number of the cat on screen, and none is none*).
The cat on screen is always one of the pages; the mapper refuses any other (*a cat on screen that is not on
the pages is refused*).

## Paging through the outing

The pages are every live cat of the cat's outing (see [outings.md](./outings.md#the-window-around-a-set-of-cats)),
newest first, side by side: a swipe towards the next page shows an older cat, towards the previous one a newer
cat. It opens on the cat it was opened for (`EncounterDetailStoreTest`, *the pages are the opened cat's outing,
newest first, with the opened cat on screen*). While there is more than one page, the centre of the bar shows
where the cat on screen is — "2 / 5", read by TalkBack as "Cat 2 of 5" — and with one page it shows nothing
(`EncounterDetailPagerTest`, *several pages show the position of the cat on screen, read as Cat n of m*; *a single
cat shows no position*).

The position turns to the page a swipe heads for as soon as the pages set off for it, not when they come to rest,
and it names the cat on screen again once they do (`EncounterDetailPagerTest`, *the position names the page a swipe
heads for before the pages come to rest*).

A swipe that comes to rest on another cat makes it the cat on screen, and *Remove this cat* removes it
(`EncounterDetailPagerTest`, *a swipe to the next page reports the older cat, once*;
`EncounterDetailStoreTest`, *settling on another page puts that cat on screen*; *after settling, a delete removes
the settled cat*). Only the state moves the cat on screen, and the pager follows it — when a cat deleted
elsewhere hands the screen to its neighbour, say (*the pager follows the cat on screen when the state moves
it*). A cat logged into the outing while the screen is open joins the pages, and the page on screen stays where
it is (*a cat logged into the outing joins the pages and the screen stays on its cat*; `EncounterDetailPagerTest`,
*a cat logged while watching keeps the cat on screen and reports nothing*). A cat leaves the pages only by being
deleted, so an outing a delete splits in two stays whole on them (`OutingPagesTest`, *an outing a delete splits in
two stays whole on the pages*). Each page shows its own cat's place (`EncounterDetailStoreTest`, *each page shows
its own cat's place*).

A cat with photos keeps its own carousel inside its page: a drag that starts on it moves the carousel first,
one item a fling, and past its end — its two add items — the rest of the drag moves on to the next cat
(`EncounterDetailPagerTest`, *a drag past the row's end moves on to the next cat*). Each page keeps its place in the
carousel while the user swipes to other cats and back (*a cat swiped away from and back to keeps its place in the
row*), and every cat's carousel draws its cards whole however many pages were swiped past (`DetailPhotoCarouselTest`,
*the photo in front fills its card on every cat paged to, after a row was scrolled*).

The pages take a moment to come to rest after a swipe, and a touch in that moment stops them. A sideways drag that
starts on the carousel then moves the carousel while the pages wait under it, and once the finger lifts they carry
on to the cat they were heading for (`EncounterDetailPagerTest`, *a drag on the row while the pages still settle
moves the row, not the pages*; *while the row is dragged during a settle the pages wait under it, then carry on*);
after a tap they carry on at once (*a tap while the pages still settle, however shaky, lets them carry on to the cat
they head for*). A drag that runs on past the carousel's end moves the pages itself, and they come to rest where it
leaves them. A drag anywhere else catches the pages as before, so a second quick swipe moves on to the cat after (*a
second flick below the row while the pages still settle moves on to the cat after*).

The cat on screen is saved with the screen, so after the process died it reopens on the cat that was on screen,
while that cat is live, and on the opened one otherwise (`EncounterDetailPagerEntryTest`, *a restored entry
reopens the cat that was on screen*; `EncounterDetailStoreTest`, *the screen starts on the restored cat while it
is live*; *a restored cat that is gone starts the screen on the opened one*). `OutingPages` keeps the pages and
the cat on screen; the Store reads every live cat and hands it each change.

### A photo of several cats

A photo of several cats is **one page**, however many cats it holds, as it is one entry in Encounters (see
[browsing-cats.md](./browsing-cats.md#a-photo-of-several-cats)); "2 / 5" counts pages, so a shot counts once
(`EncounterDetailStateMapperTest`, *a shot of three cats is one page*). The page shows one of its cats at a time:
the one the screen was opened on or last moved to, else the shot's first cat, the oldest by `createdAt`.

Under its photos the page has an **On this photo** row: every cat of the shot, oldest first, as its coat's face
or a paw for a coat nobody noted, the cat on screen ringed and read as selected. A tap on another face shows that
cat on the same page — the position does not move, nor does the page's own scroll — so its coat card, which opens
that cat's sheet, its place and *Remove this cat* are that cat's (`EncounterDetailShotTest`, *tapping a cat of the photo shows it on the same page*; *the
coat and delete act on the cat of the photo on screen*; `EncounterDetailShotRowTest`, *switching the cat of the
photo keeps the pager where it is*). *Remove this cat* removes only the cat on screen, with the usual removed state and undo;
the photo's other cats stay, and Encounters shows the shot with one cat fewer. A page of one cat has no row. Swiping away from a shot and back shows its first cat again: nothing remembers
which of its cats was on screen.

The window stays a list of cats, so handing over after a delete, the neighbouring outings and restoring by id work
as above; only the page list groups a shot, by `groupedByShot`, the same grouping Encounters uses. Every cat of a
shot keeps one page key, which is why switching between them keeps the page.

## Its number

The facts row opens with the cat's number: its place among the live cats, oldest first by the time it was
logged, with cats logged at the same instant ordered by id (`EncounterDaoNumberTest`). It reads "#62" in
English and "№ 62" in Russian, in a label filled with the theme's primary container, as tall as the outlined
ones, and a screen reader says "Cat number 62" as part of the row (`DetailNumberTest`, `DetailNumberRuTest`).
On a photo of several cats it is the number of the cat on screen.

The number is a place in the log, not an id, so it follows the log while the cat is on screen: a cat from
before it removed anywhere in the app moves it down by one, the undo moves it back, and a photo imported from
before it moves it up (`EncounterDetailStoreTest`, *a delete elsewhere or an older cat arriving renumbers the
cat on screen without leaving it*). The DAO counts it afresh on every write to the log, and
`ObserveEncounterNumber` passes it on only when it changes (`ObserveEncounterNumberTest`, `EncounterDaoNumberTest`,
*an observed number emits again when a write moves the cat*). A cat removed here and brought back with Undo shows
its number at once, and a count that moves after a swipe leaves the screen on the cat swiped to
(`EncounterDetailStoreTest`). A cat that is not live has no number, and a page without one starts its row with
the day.

## Where it was found

The card is headed **Where you met** and sits on the low container with large corners. Under its map, when it has
one (see [Its map](#its-map)), it names the place the cat was found in, the way Places files it (see
[places.md](./places.md#browsing-them)): the country's flag, then the city and the country on one emphasized line
("Barcelona, Spain"), unless the country's code is not two letters, as in Places. Under the place, where the
coordinates came from and the fix's accuracy share one quiet line ("Current location · ±12 m", or the source alone
without an accuracy), and the coordinates follow in small print (`DetailWhereTest`). The city is the cat's
cell's locality, or its admin area when it has none, and a country with no name of its own shows its
two-letter code (`ObserveEncounterPlaceTest`). A cell that names a country but no city — the cats
Places lists under No city — shows the country alone, and so does a city named
like its country, such as Singapore, which would otherwise show the one name twice
(`EncounterDetailStateMapperTest`). A cat whose cell is not named yet has no place line; the card
goes straight from its map to where its coordinates came from. The line appears while the
screen is open once the cell gets its name (`EncounterDetailStoreTest`, *the cat's place reaches the
screen once its cell is named*). TalkBack reads the city and the country with the rest of the
card and skips the flag, which would only repeat the country (`EncounterDetailScreenTest`).

The names are the cat's own cell's. Places names a country after the first of its cats whose cell
has a name for it, so the two differ only when cells of one country were named differently — in
another language, say.

## Its map

A cat the map draws, one whose coordinates lie on the globe, has a 16:10 map in its card of
the few streets around it, centred on the cat, with the cat's dot on its spot: the dot the Map tab draws
for a cat without a photo, in the cat's coat colours, or blue with no coat noted — a photographed cat
gets the dot here too, its photos being on the screen already (`EncounterDetailScreenTest`, *a cat on the
map shows a map with the cat's dot at its centre*). Setting the coat recolours it. It is drawn in the
Map tab's light or dark style, whichever the theme is, and carries the tiles' attribution
in its corner, open at first, until its ⓘ folds it away. The text and ⓘ sit directly over the tiles,
without a white container. The attribution is plain text, without the Map tab's links, and TalkBack
skips it, since the card would otherwise read it before the place. A cat with coordinates off the globe shows
no map and no pill, and a tap on its card opens nothing (*a cat not on the map shows no map*; `DetailWhereTest`).

Around the dot the map draws the fix's accuracy to scale: a primary disc at a low alpha with a primary outline,
whose radius is the accuracy at the map's street zoom and the cat's latitude, using the scale MapLibre itself
projects with. It is drawn only when the accuracy is known and the circle would be wider than the dot, so a
fix good to a few metres shows the dot alone. A circle larger than the map runs past its edges rather than
shrinking to fit (`DetailWhereTest`, `MetersPerDpTest`).

The map is a picture, not a map to explore: it takes no gesture. A drag that starts on it scrolls the
screen (*a drag across the map scrolls the screen*), and a tap on it opens the map on the cat, as a
tap anywhere else on the card does (*a tap on the map opens the map*). Should the cat's coordinates
change while the screen is open, the map is drawn afresh around the new spot rather than moved there.

Its tiles come over the network, like the Map tab's (see [map.md](./map.md#where-the-map-comes-from)).
With no connection, an area seen before loads from the cache. When not even the map's style has been
fetched yet, the map says it could not load, with no dot on an empty area; when the style has been but
this area has not, the dot sits on the map's plain background.

MapLibre's runtime is native, so neither a Compose preview nor a JVM test can start it. Under
`LocalInspectionMode` the map is a plain block with the dot and the accuracy circle at its centre, and the screen
tests switch that mode on to reach it. So the tests prove the dot's place, the circle's size and that the card's
taps and drags reach them; that the real map lets them through, and that the circle matches its streets, is checked
on a device.

## Delete and undo

**Remove this cat** — the tonal button on the error container that ends the page, centred, or the same entry
in More — deletes the cat on screen (`DetailNamesTest`). A delete is a soft delete: the row gets a `deletedAt`
and disappears from every list and count, but stays in the database until the purge worker removes it. It
deletes the cat on screen. The screen then shows a "removed" state with an Undo chip for
`Tuning.UNDO_VISIBLE`, the same window the Counter's undo uses, so the two undo gestures in the app behave
alike — even while other cats of its outing remain, and whatever happens to them meanwhile (*the removed state
holds while another cat of the outing changes mid-delete*). When the window closes the chip disappears and the
screen navigates back to the list, exactly once. Undo inside the window clears `deletedAt`; the encounter is
live again and the screen returns to its pages with that cat on screen (`EncounterDetailStoreTest`, *delete
removes the cat on screen, and undo shows it again*).

The undo affordance lives on the detail screen rather than as a snackbar on the list. A snackbar
would need the list's Store to learn about a deletion made by a different screen — cross-Store
communication the MVI rules forbid — or a shell-level snackbar whose window lives in Material's
timing and cannot be tested with virtual time. Keeping the window in `EncounterDetailStore` makes
"undo is available for exactly this long, then the screen closes" a plain unit test.

## Its photos

A cat's photos lead the screen as a carousel of the app's copies, oldest first (see
[photos.md](./photos.md#seeing-one)): Material's multi-aspect carousel, each photo a tall 4:5 card with large
corners that masks and slides as it leaves the edge, one item a fling, the first in line with the back arrow
(`DetailPhotoCarouselTest`, *a cat's photos run oldest first, 300 dp wide at 4 to 5, 8 dp apart, the first in line
with the back arrow*). After the photos come two narrower items as tall as a photo, **Take a photo** and **From
gallery**, an icon over its label on the low container (*after the photos come take a photo and from gallery,
narrower and as tall, on the low container*). While there is more than one photo, an outlined label under the
carousel names the one in front — "2 / 3", read out as "Photo 2 of 3"; scrolled onto the add items it names the
last photo (*a cat with several photos shows under the row which photo is in front*; *a cat with one photo shows no
position*; *scrolling the row to the next photo names it*). A tap on a photo opens the viewer on that photo (see
[photo-viewer.md](./photo-viewer.md); *a tap on the second photo opens the viewer on it*;
`EncounterDetailStorePhotoTest`, *a tap on a cat's second photo opens the viewer on that photo*;
`PhotoViewerEntryTest`, *a tap on the photo in the nav host's own detail entry opens that cat's viewer above it*).
When the cat gains a photo, whoever added it, the carousel moves to it, wherever it lands among the others — the
end for a new photo, further back for an older one a backup brought in (`DetailPhotoCarouselTest`, *a photo that
arrives brings the row to it*; *an older photo that arrives brings the row to it*). For a screen reader each of
several photos says which it is, "Photo 2 of 3", and a lone photo is "Photo of this cat" (*each photo of several
tells TalkBack which it is, and a lone photo says it is the cat's*).

A cat without a photo opens on itself instead: a 4:5 block in the primary container with large corners, the cat's
face — or the paw with no coat noted — "No photo yet", and a connected pair, **Take a photo** and **Gallery**
(`DetailNoPhotoTest`).

The fullscreen viewer can remove its displayed attachment after confirmation. The detail underneath
then follows the repository: the cat and its other photos remain, or the no-photo state appears after
the last attachment goes; another cat attached from the same shot is separate and stays unchanged
(see [photo-viewer.md](./photo-viewer.md#remove-from-the-cat)).

The add items at the carousel's end, and the pair on a cat with none, open the system camera or the system
picker for several images on every live cat, one that has photos included (`EncounterDetailStorePhotoTest`, *a cat
that already has a photo can still be given another*). The new photo goes after the others (*a photo taken of a
cat that has one is added after it*). A photo the cat already has is not added again, and the screen says so (*a
picked photo the cat already has is not added again, and the screen says so*). A second tap before
the camera or the picker answers opens nothing, so a double tap never opens two cameras (*a second
tap before the camera answers opens nothing*); one camera or picker is open for the whole screen at a time,
whichever page the next tap is on (*one camera or picker at a time across the pages*). The camera and the picker are opened for a named
cat, and their answer names it back — even when the process died while they were in front, since
the camera's queue and the picker remember the cat with the rest of the screen's saved state
(`PhotoLaunchersTest`; `PendingCapturesTest`). A queue saved by an older version, whose shots named
no cat, restores empty: the capture file waits for the start-up cleanup rather than landing on a
guessed cat. Once the camera or the picker hands its photos back, the attempt starts: both add items, or both
buttons of the pair, disable and a wavy progress indicator runs under the carousel or under the pair, so a tap in
the meantime opens nothing (`DetailPhotoCarouselTest`, *while a photo is being attached, both add items are
disabled*; *while photos attach, the progress runs under the row*; `DetailNoPhotoTest`, *during an attempt both
buttons are disabled and the progress runs under them*)
(`EncounterDetailStorePhotoTest`, *taking a photo while one is being attached opens nothing*). The attempt
is its cat's: it shows on that cat's page alone (*a photo attached to another page shows the attempt on that
page alone*), and it carries on, count and all, whichever cat is on screen (`EncounterDetailPickSeveralTest`,
*a pick carries on with its count when its cat comes on screen*).

A tap on the photo, on the coordinates or on *Set on map* acts on the cat of the page it was on, and opens
the viewer, the map or the location picker for that cat (`EncounterDetailStorePhotoTest`, *a tap on another
page's photo, coordinates or set on map opens it for that cat*; `EncounterDetailPagerEntryTest`, *after a swipe,
the viewer opens on the cat swiped to*; *after a swipe, set on map opens the picker for the cat swiped to*; *after a
swipe, the coordinates open the map on the cat swiped to*); a stray result naming a cat that is not on
the pages opens none of them (*a tap naming a cat not on the pages opens neither the viewer nor the map*;
*set on map names the cat it was tapped for, and a cat not on the pages opens nothing*). The coat cell,
*Take a photo*, *From gallery* or *Gallery*, the photo, the coordinates and *Set on map* send the id of the page
they sit on; *Remove this cat*, More, Undo and Back act on the cat on screen (`EncounterDetailPagesTest`, *the screen draws
the cat on screen, and its taps name that cat*; *a tap on the map of the cat on screen names that cat*).

The attempt ends only when its cat carries the photo it attached: until then the progress indicator
stays. Redrawing on `AttachPhoto`'s result instead would redraw from the last emission, which does not
have the photo yet, and enable the add items again for a moment before the photo appeared
(*a successful attach stays in progress until the photo arrives, never offering again*).

**Several from the gallery.** The picker lets the user choose up to `Tuning.ATTACH_BATCH_MAX` images; a
picker that ignores the limit — the document picker used where no photo picker is available — is cut to the
first ones chosen (`PickSeveralPhotosTest`). The photos are attached one after another, in the order
picked, after the cat's own (`EncounterDetailPickSeveralTest`, *every picked photo lands after the
cat's own, in the order picked*). While they are, both stay disabled and the progress indicator fills
as each one goes through, read out as "Attached 2 of 5 photos"; a single photo, picked or taken, shows the
bar without a count as before (*a pick of several shows how many are through as it goes*; *a single
picked photo or a camera photo shows the attempt without a count*; *a tap on either button mid-pick
opens nothing*; `EncounterDetailScreenTest`). The attempt ends only once the cat carries every photo the
pick attached (*the progress stays until the cat carries every photo the pick attached*). A pick ends in
one message at most: one photo not attached says "Photo not attached", several say how many (*one photo
of a pick not attached says so once, and the others land*; *several photos not attached say how many in
one message*); a pick the cat already has in full says so (*a pick of several the cat already has says
so once and changes nothing*); a duplicate among photos that were added is skipped without a word (*a
duplicate among photos that were added is skipped without a word*). A photo that fails still lets the
rest land. Leaving the screen mid-pick keeps the photos already attached and attaches no more (*leaving
mid-pick keeps the photos attached so far and attaches no more*); a cat removed elsewhere mid-pick gets
none of the rest, and nothing is said, since the screen already shows it gone (*the cat removed
mid-pick is given no more photos and nothing is said*).

A cancelled camera or a dismissed picker leaves the screen exactly as it was — no attempt starts
(`EncounterDetailStorePhotoTest`, *a cancelled camera or picker changes nothing*). A photo the camera
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
  the second line of defence (`EncounterDetailStoreTest`, *pressing delete twice soft-deletes exactly
  once*).
- **Undo after the window closed is a no-op** — the deletion stands and the screen has already
  asked to close (*undo after the window closed is a no-op*).
- **An id with no live encounter** — never existed, purged, or deleted from somewhere else — shows a
  "no longer here" message. It is not a crash and not an empty card pretending to be a cat (*an id
  nobody has ever seen renders as missing*; *an encounter soft-deleted elsewhere is never presented
  as live*). The distinction between "I deleted it" and "it is gone" is the Store's own flag: while the
  cat it deleted is gone, an emission without that cat is the delete taking effect, even with other cats
  of its outing still live; anything else that leaves no cat on the pages is *Missing*.
- **Deleted elsewhere while on screen** — the screen moves to the next older cat on the pages, or to the
  newer one when it was the oldest, with no undo of its own (`EncounterDetailStoreTest`, *a cat deleted
  elsewhere hands the screen to the next older cat*; *the oldest cat deleted elsewhere hands the screen to
  the newer one*). With no cat left on the pages it turns to *Missing* (*an encounter deleted elsewhere while
  on screen turns the screen to missing*), and a cat of the pages brought back elsewhere — an undo, a backup
  — returns the screen to it (*a cat deleted elsewhere and brought back returns the screen to it*).
- **The write fails** — the screen goes back to showing the cat rather than a deletion that did not
  happen, and no back-navigation fires (*a failed delete restores the loaded state*). A failed undo
  reopens the window instead of stranding the user on a closed one.
- **Leaving during the window** — system back, the back arrow or a tab tap during the undo window
  closes the screen and takes the undo with it; the deletion stands, and the window closing later
  navigates nowhere (*back during the undo window navigates back once, and the window closing adds
  nothing*). That is the defined behaviour, not an accident:
  the window is bound to the screen, and the Counter offers the same trade.
- **Pushing the same detail twice** — a double-tap on a row — puts one entry on the back stack, not
  two (`BottomNavigationTest`, *pushing the key on top again leaves the stack unchanged*).
- **The cat is deleted, or given a photo some other way, while an attempt is running** — the screen
  shows no message of its own, since it already shows what the cat became: the "removed" state,
  *Missing*, or the photo it was given first, with this one kept after it (see [photos.md](./photos.md)
  for the attempt's files).
- **Setting the coat while a photo is being attached** keeps both (see
  [coat.md](./coat.md#at-the-edges)).
- **Coordinates that name no place on Earth** — past a pole or the 180th meridian — are still shown
  as numbers, but the map does not draw that cat, so its card shows no map and no pill, and opens
  nothing (`EncounterDetailStorePhotoTest`, *a cat that is not on the map opens no map*). A cat with no
  coordinates has nothing to open either.

## Where the code lives

- `domain/…/usecase/ObserveEncounters.kt`, `ObserveEncounterPlace.kt`, `ObserveEncounterNumber.kt`, `DeleteEncounter.kt`,
  `UndoDelete.kt`; `domain/…/session/OutingWindow.kt` — the pages' outing; `domain/…/region/EncounterPlace.kt`
  — which place a cat is in
- `presentation/…/detail/` — `EncounterDetailState` (a `CatPage` per cat), `Intent`, `Effect`, `StateMapper`, `Store`;
  `OutingPages.kt` (the cats on the pages and the one on screen), `PhotoAttempts.kt` (each cat's attempt)
- `data/…/db/EncounterDao.kt` — `observeNumber`, the count behind a cat's number
- `ui/…/detail/EncounterDetailScreen.kt`, `CatPager.kt` (the pages and what each one draws), `PagerGestures.kt`
  (how the settling pages share a touch with the carousel), `DetailHeading.kt`
  (the title and the facts row), `DetailMore.kt` (More and its menu), `WhereCard.kt` (the card and the no-location
  notice), `CoatCard.kt` and `CoatSheet.kt` (the coat, see [coat.md](./coat.md#changing-it-later)),
  `DetailPhotoCarousel.kt` (the photos and the add items), `NoPhotoBlock.kt` (a cat without a photo), `AttachingBar.kt`,
  `PhotoInteraction.kt` (a photo tap's payload);
  `ui/…/components/BackBar.kt` — the bar, `Labels.kt` — the facts' labels, `Flag.kt` — a flag TalkBack skips,
  `NoticeCard.kt` — the notice; `ui/…/map/SpotMap.kt` — the card's map, the cat's dot and the accuracy circle,
  `MetersPerDp.kt` — the map's scale
- `app/…/navigation/EncounterDetail.kt` (the key), `BottomNavBackStack.push()`,
  `EncounterDetailDestination.kt` (the destination composable, which saves the cat on screen with the screen,
  wired into `CatsRadarNavHost.kt`, which
  pushes `PhotoViewer` on the photo's tap, and on the coordinates' tap pushes `CatOnMap` for the cat on
  screen, and on *Set on map* pushes `LocationPicker` for the cat the
  tap named), `PhotoLaunchers.kt` (the camera and the cat's photo picker), `app/…/photo/PendingCaptures.kt`
  (which camera a result belongs to, and for which cat)

## Not built yet

**+** on the **On this photo** row, and **Another cat on this photo** on a page of one cat — adding a cat to a
photo from here — are not built; a photo gains cats only when it is counted in the coat sheet after the shutter.

A cat's photos cannot be reordered (see `photos.md`).

Moving on from the first or the last page to the neighbouring outing is not built yet: the pages end at the
outing's newest and oldest cats.
