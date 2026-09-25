# Many photos per cat — design

- **Date:** 2026-09-25
- **Status:** approved by the owner in chat, 2026-09-25
- **Decomposition:** [docs/tbd/decompositions/2026-09-25-many-photos-per-cat.md](../../tbd/decompositions/2026-09-25-many-photos-per-cat.md)
- **Builds on:** [2026-09-21-cats-radar-design.md](./2026-09-21-cats-radar-design.md) §4.2a, §5;
  [2026-09-24-photo-viewer-and-gallery-link-design.md](./2026-09-24-photo-viewer-and-gallery-link-design.md)

## What the owner asked for

A cat can have many photos, added from its detail screen.

Owner decisions, 2026-09-25:

- **Every photo lives in one table, the first one included.** The five photo columns leave
  `encounters`; a cat's photos are rows of `encounter_photos`. Keeping the first photo on the cat's row
  and only the others in a table was offered and rejected: two homes for one thing.
- The work is tested and reviewed thoroughly — the migration above all, since it moves data that
  exists only on users' phones.

## The model

```kotlin
data class EncounterPhoto(
    val id: String,
    val encounterId: String,
    val photoPath: String,          // the app's copy, relative to the photo directory
    val thumbPath: String?,         // null when the thumbnail failed to write
    val galleryUri: String?,        // the camera original the app saved to the gallery
    val sourceMediaUri: String?,    // the gallery item a picked photo came from
    val sourceDigest: String?,      // SHA-256 of the bytes the source handed over
    val deviceId: String,           // the install that recorded galleryUri / sourceMediaUri
    val addedAt: Instant,
)
```

`Encounter` loses `photoPath`, `thumbPath`, `galleryUri`, `sourceMediaUri` and `sourceDigest` and gains
`photos: List<EncounterPhoto>`, oldest first (`addedAt`, then `id`). The first is the cat's **cover**:
what a tile, a pair row and the coat prompt show. A cat has a photo exactly when `photos` is not empty.

A photo row is written once and never edited. Removing, reordering or choosing a cover is not built;
none of it needs a column today, and a later `deletedAt` is an additive migration.

**`deviceId` on the photo, not only on the cat.** Gallery ids mean something only on the phone that
recorded them. Until now a row named only the install that logged the cat, so a photo attached on
another phone kept no link at all. A photo naming its own install can keep its links wherever it was
added and offer them only there.

## Storage

`encounter_photos`: primary key `id`; `encounterId` a foreign key to `encounters(id)` with
`ON DELETE CASCADE`; indices on `encounterId` and `sourceDigest`. Room's generated `onOpen` turns
`PRAGMA foreign_keys` on, which the walks' track points already rely on.

Repository contract:

| Operation | What it writes |
|---|---|
| Every read (`observeAll`, `observeById`, `loadEvery`, `loadDeletedBefore`, `findBySourceDigest`) | Returns cats with their photos, ordered |
| `insert(encounter)` | The cat and all of its `photos`, in one transaction |
| `update(encounter)` | The cat's own columns only; its photos are never touched |
| `addPhoto(photo): Boolean` | One photo, only while its cat is live; true when written |
| `addPhotos(photos)` | Each photo whose `id` is not here yet; used by a backup import |
| `findBySourceDigest(digest)` | A live cat that has a photo with that digest |
| `purgeDeletedBefore(cutoff)` | The rows; their photo rows go by cascade (files are the caller's, first) |

## The migration, v3 → v4

A hand-written `Migration(3, 4)` — an `AutoMigration` cannot move data before dropping a column. In
one transaction:

1. Create `encounter_photos` and its two indices, with the exact SQL Room exports for version 4.
2. Copy every cat's photo: `INSERT INTO encounter_photos SELECT id, id, photoPath, thumbPath,
   galleryUri, sourceMediaUri, sourceDigest, deviceId, createdAt FROM encounters WHERE photoPath IS NOT
   NULL`.
3. Drop `index_encounters_sourceDigest`, then `ALTER TABLE encounters DROP COLUMN` each of the five.

**Why `DROP COLUMN`, not the rebuild Room generates.** A rebuild drops `encounters`; while foreign keys
are on, `DROP TABLE` first deletes every row, and the cascade would take every photo just copied. The
bundled SQLite supports `DROP COLUMN`, which rewrites the table in place and deletes nothing. It refuses
an indexed column, hence step 3's order.

**What the moved photo becomes.** Its `id` is its cat's `id`: two phones holding the same cat migrate
it to the same photo, so a backup between them recognises it. Its `deviceId` is its cat's, which keeps
today's link rule exactly for every existing photo. Its `addedAt` is the cat's `createdAt`, which keeps
it first. A cat whose `photoPath` is null gets no row, whatever its other photo columns hold.

**Tests** (`CatsDatabaseMigrationTest`, JVM with the bundled driver):

- a v3 database holding a camera photo with a gallery original, a gallery attachment with a picked
  item, a photo without a thumbnail, a soft-deleted cat with a photo, a cat from another install, and a
  tally without a photo migrates to v4 with every cat's other columns unchanged, one photo row per cat
  that had one carrying exactly its values, and none for the tally;
- the same from a v1 and a v2 database, through every migration in between;
- the migrated file opened by the app's own database builder reads the cats with their photos, and a
  purge removes the photo rows with their cat — proving the cascade on the real configuration;
- each assertion is seen failing once against a deliberately broken migration before it is trusted.

## Backup

Format 4 adds `encounter_photos.json`, one record per photo; an encounter record carries no photo
fields any more. The `photos/` entries are every file a photo row points at, as before.

Reading: an archive of format 3 or older still carries the photo in its encounter records; it becomes
one photo by the migration's rule (same `id`, `deviceId`, `addedAt`). A format 4 archive without
`encounter_photos.json` was cut off and is refused as unreadable. A photo whose path climbs out of the
photo directory refuses the archive, as a cat's did.

Merging: photos merge by their own `id`, independently of their cat's row. A photo the archive has and
this phone does not is added, but only to a cat that is live here after the merge. A photo already here
is never replaced — the local copy is the one the app has been rendering. A cat whose row is unchanged
but gains photos counts as updated. Files keep today's rule: restored only where none is here.

The version bump means an app before it refuses a new archive as too new rather than losing every photo
after a cat's first.

## Attaching photos

`AttachPhoto` adds a photo to any live cat, with or without photos:

- **The same photo twice on one cat is not added.** Its digest is taken before any copy is written; a
  match on this cat's photos returns `AlreadyThere` and costs no disk. The same photo on two different
  cats stays allowed.
- **Links are kept on every cat**, another install's included, now that the photo names its install.
- **Unreadable image, failed write, cat deleted meanwhile, leaving mid-attempt** — as today: the cat is
  unchanged and the attempt removes its own files; once the write lands they are the cat's.
- The gallery setting and the picked-item rule are unchanged.

## The detail screen

- The cat's cover, with a count badge when it has more than one photo; a tap opens the viewer on the
  cover, and the viewer pages through the rest. A photo pager here would share its swipe with the pager
  across the outing's cats (see [2026-09-25-outing-pager-design.md](./2026-09-25-outing-pager-design.md)).
- **Add photo** — *Take a photo* and *Choose from gallery* — is always offered on a live cat, under
  its photos, or in place of them on a cat with none.
- *Choose from gallery* opens the system picker for several images, up to `Tuning.ATTACH_BATCH_MAX`; a
  picker that ignores the limit is cut to it, as an import is.
- The picked photos are attached one after another. While they are, both buttons are disabled and a
  progress bar shows how many are done; it stays until the observed cat carries every photo attached,
  so the section never flickers back early. The badge then counts them.
- Messages after a batch: any photo that could not be attached → "Photo not attached", or "N photos
  not attached" for several; every picked photo already on the cat → "Already on this cat". A
  duplicate among photos that were added is skipped silently.
- Leaving mid-batch keeps the photos already attached and attaches no more.
- A camera photo is one per shot, discarded afterwards as today.

## The viewer

- `PhotoViewer(encounterId, photoId)`; a key restored without a `photoId` opens the cover.
- A horizontal pager over the cat's photos opens on the tapped one; pinch, double-tap and pan act on
  the photo, and a swipe at a photo's edge moves to the next.
- *Open in gallery* and its checks act on the photo on screen, with that photo's own link.
- The viewer closes when its cat disappears or has no photos, as today.

## Unchanged

Encounters tiles and pair rows (they show the cover), "With photo" (cats with at least one photo), the
Counter's camera, the coat prompt, gallery import (each picked photo is still a cat with one photo),
the map, the widget and analytics events.

## Not built

Removing or reordering a cat's photos, choosing its cover, a photo count on a tile, swiping between
cats in the viewer, sharing.
