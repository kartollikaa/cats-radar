# Backup

Your cats are yours: an archive you can write somewhere of your choosing and read back, on this
device or another one. No account, no server, no sync.

**Nothing reaches this yet.** The archive and the merge rules exist and are wired into the object
graph; the Settings buttons that call them are the next slice, so today no tap can export or import.

## What goes in

Live encounters and every place cell. **Deleted cats stay home** — a backup is what you have, not
what you threw away. That single decision is what makes the merge rules below as short as they are:
a tombstone never travels, so an imported row is always a live one.

## Merging, not replacing

Importing never wipes what is here. Every row is reconciled on its own, by `id`:

- **A cat this device has never seen** is added.
- **A cat both know about** keeps whichever copy was edited later. A tie keeps what is already here,
  which is what makes importing a backup of the current state write nothing at all.
- **A cat deleted here** stays deleted, unless the archive's copy was edited *after* the deletion —
  in which case the user has been using another device since, and that later edit is the more recent
  statement of intent.

There is no server to arbitrate, so each rule settles the conflict from the two rows alone.

## Place cells

A cell is a patch of map with a name attached, expensive to obtain and worth carrying between
devices.

- **A name beats no name.** A cell someone's device resolved wins over one still pending or given up
  on, however many attempts went into the local one.
- **Between two names, the fresher lookup wins.**
- **Between two unnamed cells, the local one stays** — its `attempts` is what the geocoding worker
  paces its retries by, and an import must not reset that.

## The archive

A ZIP holding `manifest.json`, `encounters.json`, `placecells.json`, and a `photos/` entry for every
file the rows point at. Instants travel as epoch milliseconds and enums as their names, so a future
version reordering a column changes nothing.

The manifest records `formatVersion`, when it was exported, which device wrote it, and that build's
version name — the last being the only thing that could ever explain an archive a later build cannot
read.

**Photos are taken from the rows themselves.** The writer reads each encounter's `photoPath` and
`thumbPath` out of photo storage; a file that has gone missing since the row was written is skipped
rather than failing the export, because the rest of the archive is still worth having.

On import a photo is restored **only where none is already here** — the local copy is the one the app
has been rendering, and an archive should not quietly replace it.

## At the edges

- **An archive from a newer version of the app is refused**, not partially read: its rows may carry
  fields this version would silently drop. Nothing is written.
- **An unreadable archive is refused the same way** — not a ZIP, no manifest, or rows that will not
  parse. Both reasons reach the caller, which decides what to say.
- **A photo entry whose name climbs out of the photo directory refuses the whole archive.** Photo
  storage rejects the path, and an archive that tried it is not one to take rows from either.
- **Reading the local side uses `loadEvery`**, the one read that sees soft-deleted rows. Every other
  read hides them, and a merge that could not see a deletion would let an old archive reinsert the
  cat as if it were new.

## Where the code lives

- `domain/…/backup/BackupMerge.kt` — the rules, as one pure function
- `domain/…/backup/BackupContents.kt` — what an archive holds, and what a merge decided
- `domain/…/usecase/ExportBackup.kt`, `ImportBackup.kt`
- `domain/…/platform/BackupArchive.kt` — the reader/writer seam
- `data/…/backup/BackupRecords.kt` — the serialized shape and its mappers
- `data/…/androidMain/backup/ZipBackupArchive.android.kt` — the ZIP itself

## Not built yet

The Settings entry points and their system file picker, and the workers that would run an export or
import in the background with progress. Today both use cases run wherever they are called.
