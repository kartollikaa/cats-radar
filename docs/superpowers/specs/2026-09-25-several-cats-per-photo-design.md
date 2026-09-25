# Several cats on one photo — design

- **Date:** 2026-09-25
- **Status:** approved by the owner in chat, 2026-09-26
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

Format **5** writes `shotId` on each photo record. A photo that starts its shot writes none, so its
record reads exactly as format 4 wrote it. A format 4 or older archive has no `shotId` anywhere, so
each of its photos is the first row of its own shot. Photos still merge by their own `id`, and
`shotId` travels with its row. A shot whose first row is not in the archive still groups by that id,
and joins the first row once a later import brings it. The archive carries every cat's own copy of
the files, as it carries every file a photo row points at today.

**The format number is what protects an older app.** The reader ignores keys it does not know. An app
before this one, handed an archive that still said format 4, would read it, drop every `shotId`, and
write the shots out as separate cats on its next export. Saying 5 makes that app refuse the archive as
too new instead, the way it refuses any newer format.

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

## Keeping export, import and the migration whole

Merging needs no new rule. `BackupMerge` carries photos as whole rows keyed by their own id, the
files already go one row at a time, and a gallery import already skips a photo by its digest, which
every cat of a shot carries. What can break is a **silent loss of `shotId`**: at a mapping that
forgets the field, at a format number that does not move, or in the migration. The shots would then
come back as separate cats, and every test that looks only at cats would stay green. So:

- **`EncounterPhoto.shotId` has no default value.** Each place that builds a photo has to state it,
  so a new construction site cannot forget it. The Room entity has no default either: Room fills a
  missing column with null by itself. Only the archive's record needs a default of null, to read
  older records, so its mapping is the one place that can still drop the field without a compile
  error. The round trips below cover it and every other mapping.
- **Every round trip carries a non-null `shotId`.** Each of these tests uses a shot of three cats, one
  with no coat:
  - the entity mapper, both directions;
  - the archive records, both directions;
  - `ZipBackupArchiveTest.everyFieldOfEveryRowSurvivesTheRoundTrip`, written and read back as a
    zip;
  - `BackupRestoreTest` on a real database: export, wipe, import. It brings back the shot as one
    group with every cat's coat and its own files. Imported again, it changes nothing.
- **Older archives.** A format 4 archive, and a format 3 one through `carriedPhoto`, read with every
  photo starting its own shot and every other field unchanged (`ZipBackupReaderOlderFormatTest`). The
  archive says format 5, so a format 4 reader refuses it (mirroring
  *anArchiveSaysItIsFormatFourSoAnAppBeforeThePhotoListRefusesIt*).
- **Merging across phones.** Each of these is a `BackupMerge` test:
  - a phone holding the first two cats of a shot imports an archive with the third, and the third
    joins the shot;
  - a cat added on another phone with **+** joins the shot here;
  - a first cat deleted here after the export stays deleted, and its shot's other cats stay grouped;
  - an archive holding only a later cat of a shot restores it grouped by the missing first row's id.
- **Files.**
  - Purging one cat of a shot leaves every other cat's copy on disk (`PurgeDeletedTest`).
  - An undone gallery import soft-deletes the imported cat only. A cat added to that photo with **+**
    stays, with its own files (`UndoImportTest`).
  - Importing the shot's photo from the gallery again is skipped while any of its cats is live.
- **The migration v4 → v5**, in `CatsDatabaseMigrationTest` with the bundled driver:
  - a v4 database holding the kinds of photo the v3 → v4 tests use (a camera photo with a gallery
    original, a picked item, no thumbnail, a soft-deleted cat, another install's cat, a tally) reaches
    v5 with the same number of cats and photos, every value unchanged, and a null `shotId` on every
    photo;
  - the same with foreign keys on, since a table rebuild with them on is what could take rows away;
  - the app's own builder opening a v1, a v2 and a v3 file reaches v5. The existing purge test, which
    removes a cat's photo rows with it, now runs at v5;
  - `5.json` exported, with its test-asset copy kept identical by `SchemaAssetSyncTest`.
- **Each of these tests is seen failing once, on purpose**, before it is trusted. The breaks: a
  mapping that drops `shotId`, the format number left at 4, and a migration that loses a row. A
  deliberately broken build must turn each test red.
- **On a device.** After the storage slice (S1), a build of `main` with real data (a camera photo, a
  gallery photo, a deleted cat, a walk) is upgraded in place, and everything reads back. After the
  format slice (S2), an export from each build imports into the other, and the old one refuses the
  new archive as too new. The same export and import run on the **release** APK, since R8 can strip
  what serialization needs while every JVM test stays green. After the coat sheet (S5), the same
  runs on a real shot of several cats: export, clear the app's data, import, and the shot comes back
  as one tile with its badge.

## Slices

Six slices, each safe to ship on its own; the map is
[docs/tbd/decompositions/2026-09-25-several-cats-per-photo.md](../../tbd/decompositions/2026-09-25-several-cats-per-photo.md).
Nothing creates a shot of several cats until S5, and by then storage, backup, the use case and
Encounters all handle one.

1. **S1 Photos know their shot.** `shotId` on `EncounterPhoto`, database v5 and its migration tests.
2. **S2 Backup format 5 carries shots.** The record field, the format number, older formats, and the
   round trips.
3. **S3 Adding cats to a photo.** `AddCatsToPhoto`, the repository's all-or-nothing insert, the
   location rules, and analytics.
4. **S4 Encounters shows one entry per shot.** Packing, the badge, opening the first cat, and
   selecting by shot, in both layouts.
5. **S5 Counting cats in the coat sheet.** **Several**, the tray, the paw, **Save N cats**, strings in
   EN and RU.
6. **S6 On this photo on the detail screen.** The row, switching between cats, and **+**.

Each slice updates its feature documents in the same PR: `data-model.md` (S1); `backup.md` (S2);
`photos.md`, `location.md` and `analytics.md` (S3); `browsing-cats.md` (S4); `coat.md` (S5);
`encounter-detail.md` (S6).
