# Several cats on one photo — design

- **Date:** 2026-09-25
- **Status:** design approved by the owner in chat, 2026-09-25; written spec awaiting review
- **Builds on:** [2026-09-21-cats-radar-design.md](./2026-09-21-cats-radar-design.md) §1 (counting),
  [2026-09-25-many-photos-per-cat-design.md](./2026-09-25-many-photos-per-cat-design.md)

## What the owner asked for

One photo can show several cats, and taking it counts them all at once. Each of those cats can have
its own coat. In Encounters such a photo carries a badge with its number of cats when there is more
than one.

Owner decisions, 2026-09-25:

- **Choosing the coats:** the coat sheet after a photo keeps its one-tap answer for a single cat and
  gains a **Several** control that turns it into a counting mode — each face tapped adds one more cat
  of that coat. Rejected: always counting (one extra tap for the common single cat) and marking each
  cat on the photo itself (slow in the moment, and it would need positions stored).
- **Encounters:** a photo of several cats is **one tile with a badge**, and selecting it selects all
  of its cats. Rejected: one tile per cat, each repeating the same photo.

## The model

**Every cat stays its own encounter.** A photo of three cats is three encounters, each with its own
coat, and each counted once, so the main spec's rule "strictly +1 per encounter" still holds, and so
does its non-goal "quantity per encounter". Totals, outings, rates, By coat, With photo, the map, the
widget and streaks all stay right without changing. A count or a list of coats on one encounter was
considered and dropped: every counter in the app would need rewriting, and the counting rule would
stop being true.

**The cats of one photo share a shot.** `EncounterPhoto` gains `shotId: String?`: the id of its shot's
first row, or null on that first row itself. A photo's **shot** is `shotId ?: id`, so a row joining a
shot always takes the shot, never the id of the row it was copied from. A shot's cats are the live
cats that have a photo row with that shot. A row is still written once and never edited: joining a
shot never touches the rows already in it, because the new row points at the first one.

**Each cat keeps its own copy of the files.** A new cat in a shot gets its own photo row with its own
copy of the app's copy and thumbnail, named after its own photo id. The purge, an attempt's clean-up
and the backup's file list keep working one row at a time, exactly as they do now: a file shared
between rows would need the purge to count references before it deletes one. The cost is one extra
copy on disk for each extra cat. The links (`galleryUri`, `sourceMediaUri`, `sourceDigest`) and the
`deviceId` that names where the links are valid come from the source row unchanged.

**What a new cat in a shot copies from the cat it joins:** `occurredAt`, `tzOffsetMinutes`, `kind`,
`origin`, and every location field (`lat`, `lon`, `accuracyMeters`, `locationSource`,
`locationFixedAt`, `geohash`, `placeCellId`). It has its own `id`, `createdAt`, `updatedAt` and coat.

## Storage

- Database **v5** adds the nullable column `encounter_photos.shotId` and an index on it, by an
  automatic migration. Every photo already stored reads as the first row of its own shot.
  `CatsDatabaseMigrationTest` opens a v4 database with photos and checks that each one comes through
  with a null `shotId` and every other value unchanged.
- **Adding cats to a shot** is one repository call that inserts every new cat with its photo row in
  one transaction, and only while the source cat is still live. Either all of them are written or
  none is.

## Backup

Format **5** writes `shotId` on each photo record. A format 4 or older archive has none, so each of
its photos is the first row of its own shot. Photos still merge by their own `id`, and `shotId`
travels with its row. A shot whose first row is not in the archive still groups by that id, and joins
the first row once a later import brings it. An app before format 5 refuses a new archive as too new,
as it does for every format bump.

## Adding cats to a photo

`AddCatsToPhoto(sourceEncounterId, photoId, coats: List<CatCoat?>)` adds one cat per entry to the shot
of that photo:

- It copies the files first, then writes all the cats in one transaction. If anything fails, the files
  it copied are removed and no cat is added. If the source cat has been deleted in the meantime, it is
  left alone, the attempt's files are removed, and nothing is added.
- A new cat copies the source's location as it is at that moment. A source still waiting for its fix
  gives `NONE`, and the same outing's backfill fills it in when that fix resolves (see
  `location.md`). A cat added from the Counter right after the shutter with `NONE` also goes to the
  background location attach, so no cat is left without a location because its write landed after
  the backfill ran. A cat added later from the detail screen asks for no fix, since the phone may be
  somewhere else by then.
- Each new cat logs `cat_logged`, like any other saved cat, with its copied kind and origin.

## The coat sheet after a photo

The sheet as it is today — thumbnail, *What coat?*, the eleven faces, **Not now** — gains a
**Several** button beside **Not now**. A face tapped outside the counting mode still sets that coat on
the photographed cat and closes the sheet: one tap for one cat.

**Several** turns the same sheet into counting mode:

- The title becomes the number of cats on the photo ("3 cats on this photo"), and a **tray** under it
  shows the photo's thumbnail and one face per cat, in the order they were tapped.
- Each face in the grid adds a cat of that coat to the tray and shows how many of that coat the tray
  holds. A **paw** cell after the eleven coats adds a cat whose coat nobody saw.
- Tapping a cat in the tray takes it out.
- **Save N cats** appears once the tray holds a cat. It closes the sheet, then sets the first tray
  entry's coat on the cat the camera already saved, and adds the rest to its shot with
  `AddCatsToPhoto`.
- The tray holds at most `Tuning.SHOT_MAX_CATS` cats. Past that, the faces stop adding.
- Leaving the counting mode any other way — **Not now**, a swipe down, a tap outside the sheet,
  back — is the same as **Not now** today: the photographed cat stays, with no coat, and no other cat
  is added.

The sheet's existing edge rules hold in both modes. A newer photo takes the sheet over, and the tray
goes with the old photo. The sheet closes before anything is written. A count changing underneath
leaves the sheet open, tray included. The tray is Store state, so it survives a rotation. After a
process death it is lost along with the question, as the coat is today.

A write that fails after **Save** shows one message and leaves the photographed cat as it is: with the
first coat if that write succeeded, and with no new cats.

Screen readers read a tray entry as its coat and "remove", and a grid face as its coat and how many
the tray holds.

## Encounters

- **One tile per shot.** Cats whose cover is a photo of the same shot collapse into one entry, both in
  the grid and in the list. The entry shows that photo, with a **badge** of a cat and the number of
  cats in the entry when there are more than one. In the grid a shot packs like a single photo: it
  pairs, runs and tiles by the rules in `browsing-cats.md`.
- **A tap opens the shot's first cat**, the oldest by `createdAt`, which is the one the camera or the
  import saved.
- **Selection is by shot.** A long press or a selecting tap adds or removes every cat of the entry.
  The bar counts cats, not entries. Delete soft-deletes all of them as one batch with one undo.
- **The entry's time and location** are its cats' own, which are the same for every cat of a shot.
  For accessibility the entry also announces its number of cats.
- A cat that has a shot's photo but whose cover is another photo keeps its own entry. The shot's entry
  counts only the cats it shows.

## The detail screen

- Under the photo pager, the photo on screen has an **On this photo** row. When the shot has more than
  one live cat, the row shows each cat's face (a paw for no coat), with the cat on screen ringed, and
  ends with **+**. On a photo of one cat the row is a single **Another cat on this photo** button. A
  live cat always has one of the two.
- **Tapping another cat** replaces this screen with that cat's, so moving between a photo's cats never
  stacks screens: back still returns to wherever the first one was opened from.
- **+** adds one cat with no coat to the shot, with `AddCatsToPhoto`, and replaces this screen with the
  new cat's, whose coat picker is right there. This is also how an imported photo, or one counted
  wrong at the shutter, gains its cats.
- Taking one cat off a photo is that cat's **Delete**, with its undo, as now. The row on its siblings
  updates from the flow.

## Unchanged

Totals, outings, rates and By coat in Statistics; With photo (each cat has its own copy); the Counter's
tally, undo and "+N" burst, which never covered photos; the widget; gallery import (each picked photo
is still one cat, and **+** on its detail adds more); the photo viewer; the Places area list and the map,
where a shot's cats are separate dots at one point, as any two cats logged in one spot are today.

## Not built

Marking where each cat is on the photo; counting cats on the photo automatically; a shot badge on the
map or in the Places area list; moving a cat from one shot to another; changing a shot's first cat.

## Slices

Each slice ships safely on its own. Nothing creates a shot of several cats until slice 4, and by then
storage, backup and Encounters all handle one.

1. **Photos know their shot.** `shotId` on `EncounterPhoto`, database v5 and its migration test,
   backup format 5 and the older-format reading.
2. **Adding cats to a photo.** `AddCatsToPhoto`, the repository's all-or-nothing insert, the location
   rules, and analytics.
3. **Encounters shows one entry per shot.** Packing, the badge, opening the first cat, and selecting
   by shot, in both layouts.
4. **Counting cats in the coat sheet.** **Several**, the tray, the paw, **Save N cats**, strings in EN
   and RU.
5. **On this photo on the detail screen.** The row, switching between cats, and **+**.

Each slice updates its feature documents in the same PR: `data-model.md` and `backup.md` (1);
`photos.md`, `location.md` and `analytics.md` (2); `browsing-cats.md` (3); `coat.md` (4);
`encounter-detail.md` (5).
