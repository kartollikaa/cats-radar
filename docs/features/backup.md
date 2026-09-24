# Backup

Your cats are yours: an archive you can write somewhere of your choosing and read back, on this
device or another one. No account, no server, no sync.

**Settings → Backup** has an Export and an Import button. Both run in a worker, so leaving the
screen mid-run cannot leave a half-written archive, and the screen reads the run's state back
rather than remembering it. Nor can an import leave a half-merged database: it reads, merges and
writes every row inside one transaction, so an import that fails part-way — an SQLite error, a full
disk, a stopped worker, a killed process — leaves the database exactly as it found it.

## What goes in

Live encounters, every place cell, and every walk with its route. **Deleted cats stay home** — a
backup is what you have, not what you threw away. That single decision is what makes the merge
rules below as short as they are: a tombstone never travels, so an imported row is always a live
one.

## Merging, not replacing

Importing never wipes what is here. Every cat is reconciled on its own, by `id`:

- **A cat this device has never seen** is added.
- **A cat both know about** keeps whichever copy was edited later. A tie keeps what is already here,
  which is what makes importing a backup of the current state write nothing at all.
- **A cat deleted here** stays deleted, unless the archive's copy was edited *after* the deletion —
  in which case the user has been using another device since, and that later edit is the more recent
  statement of intent.
- **A cat the archive lists more than once** is still one cat. Its copies settle among themselves
  first — the later edit wins, a tie keeps the one listed first — and only that copy meets the rules
  above. It is counted once, as added, updated or unchanged: the counts are cats, not rows.

There is no server to arbitrate, so each rule settles the conflict from the rows alone.

## Place cells

A cell is a patch of map with a name attached, expensive to obtain and worth carrying between
devices.

- **A name beats no name.** A cell someone's device resolved wins over one still pending or given up
  on, however many attempts went into the local one.
- **Between two names, the fresher lookup wins.**
- **Between two unnamed cells, the local one stays** — its `attempts` is what the geocoding worker
  gives the cell up by, and an import must not reset that.
- **An unnamed cell this device has never seen arrives untried**: `PENDING`, no attempts, no name,
  exactly as if a cat here had just landed in it — whatever the exporting device concluded. Its
  `FAILED` or `UNAVAILABLE` is a verdict about that device's geocoder, not this one's, and nothing
  here ever retries a cell in either state, so taken as written it would read as "Not named yet" for
  good. A cell imported before this rule keeps the state it arrived with: importing the archive
  again meets it as an unnamed local cell, which stays.

## Walks

A walk is matched by `id`, as a cat is: the later edit wins, and a tie keeps the walk here.

- **A route is never shortened.** A point is one walk at one moment, and the two copies' points are
  merged. A point only the archive has is added; one both have is not written twice. An archive of
  the same walk taken earlier in it therefore adds nothing, and one taken later adds what it has
  on top.
- **A walk still on in the archive arrives ended** at its last point, or at its start when it has
  none, because it was being recorded on another phone and cannot carry on here. The exception is
  the walk on here, which stays on. Either way at most one walk is ever on.
- **A walk ended that way reaches the end** of the longer route a later archive of it brings.

## The archive

A ZIP holding `manifest.json`, `encounters.json`, `placecells.json`, `walks.json`,
`trackpoints.json`, and a `photos/` entry for every file the rows point at. Instants travel as epoch milliseconds and enums as their names, so a future
version reordering a column changes nothing.

The manifest records `formatVersion`, 2 since walks joined the archive, when it was exported, which device wrote it, and that build's
version name — the last being the only thing that could ever explain an archive a later build cannot
read.

**Photos are taken from the rows themselves.** The writer reads each encounter's `photoPath` and
`thumbPath` out of photo storage; a file that has gone missing since the row was written is skipped
rather than failing the export, because the rest of the archive is still worth having.

On import a photo is restored **only where none is already here** — the local copy is the one the app
has been rendering, and an archive should not quietly replace it.

**A photo reaches the photo directory only once the whole archive is accepted.** While the archive
is read, its photos are unpacked beside the photo directory; they move into place only after the
manifest, every row and every path in them have been judged, and the unpacked copies are deleted
either way. An archive that is refused, cut off part-way or damaged therefore leaves nothing behind
— not even the part of a photo it managed to read, which would otherwise stay for good, since a
photo is only ever restored where none is here.

**Photos are not part of the merge's transaction**, though: a file cannot join a database
transaction, and the photos are in place before any row is merged. An import whose merge then fails
writes no rows but can leave behind the photos it restored. They are the archive's own bytes,
written only where no file was here, so importing the same archive again finds them and keeps them.

## At the edges

- **An archive from a newer version of the app is refused**, not partially read: its rows may carry
  fields this version would silently drop. Nothing is written. It is judged by its manifest before
  any row is read, wherever the manifest sits in the ZIP, so rows this version cannot even parse
  still say "newer version", not "not a backup". That is why the walks raised the version: an app
  from before them refuses an archive rather than losing its walks.
- **An archive from before walks** still imports, with no walks in it.
- **An unreadable archive is refused the same way** — not a ZIP, no manifest, rows that will not
  parse, or a file cut off inside one of its entries. Both reasons reach the caller, which decides
  what to say.
- **A file cut off between two entries** looks, to a ZIP read entry by entry, like its end. An
  archive of this version's format always carries all five lists, so one that lacks any of them was
  cut off and is refused as unreadable. The lists come before the photos, so a clean cut after them
  can only lose photos: the cats arrive, and those whose photos were past the cut show the
  placeholder until an archive that has them is imported.
- **A photo entry whose name climbs out of the photo directory refuses the whole archive.** Photo
  storage rejects the path, and an archive that tried it is not one to take rows from either.
- **So does a row whose photo or thumbnail path climbs out of it.** Every screen that shows a cat
  resolves those paths through photo storage, which refuses them by throwing, so such a row would
  not be a cat without a photo but a screen that cannot open.
- **A cat whose location is not a point on the globe is imported without one.** A latitude beyond
  ±90, a longitude beyond ±180, only one of the pair, a source with no coordinates, or coordinates
  on a cat marked `NONE`: the cat arrives at `NONE`, with no coordinates, accuracy, fix time,
  geohash or place cell, rather than the whole archive being refused over one hand-edited row. The
  cat is otherwise untouched — its `updatedAt` included, so importing the same archive again still
  writes nothing — and the merge still picks whole rows: if that copy wins over the one here, it
  wins without a location.
- **A located cat's geohash and place cell are derived from its coordinates, not read from the
  archive**, so a hand-edited geohash cannot disagree with the point it claims to describe. A cell
  the archive does not carry is created pending, exactly as for a cat located on this device, and
  a cell it does carry keeps its name.
- **An archive's place cell is placed by its id, not by the centre written next to it.** A
  `cellId` that is not a geohash of `Tuning.PLACE_CELL_PRECISION` as this app writes one — exactly
  that many characters of the geohash alphabet, lowercase — is dropped, not the archive with it: no
  imported cat can point at such a cell, since a cat's cell is derived from its coordinates. A
  valid id's centre is derived from the id, so a hand-edited centre off the globe, or in another
  city, is never the point the geocoder is asked about. A named cell's name, status and attempts
  arrive as written; an unnamed one arrives untried, as above. The merge rules then decide between
  it and the local one.
- **An archive that lists one cell more than once settles its own rows first**, by the same rules —
  a name beats no name, the fresher name beats the older, and a tie keeps the row listed first — and
  only the survivor is weighed against the cell here. Weighed one by one against the local cell,
  every row that beat it would be written and the last would stick, so a pending row listed after a
  named one would erase the name.
- **An archive that lists one cat more than once imports it once.** Taken row by row, a cat new here
  would be inserted twice, and the second insert would fail the import with every cat before it
  already written; a cat already here would end up as whichever row came last, older or not.
- **An import that fails part-way writes no rows at all.** Without the transaction, the cats and
  walks written before the failure would stay, place cells could be missing for cats that point at
  them, a walk could arrive without its route, and the worker would still report the import as
  failed.
- **The merge reads what is here inside the same transaction it writes in.** A change landing
  between the read and the writes — a cat deleted here while the import runs — would otherwise be
  overwritten by a decision made without it. Instead, that write waits until the import has finished.
- **Reading the local side uses `loadEvery`**, which returns soft-deleted rows too. The live reads
  hide them, and a merge that could not see a deletion would let an old archive reinsert the cat as
  if it were new.

## Where the code lives

- `domain/…/backup/BackupMerge.kt` — the rules, as one pure function; `WalkMerge.kt` — the walks'
  rules
- `domain/…/backup/BackupContents.kt` — what an archive holds, and what a merge decided
- `domain/…/backup/ImportedLocation.kt` — what an imported cat keeps of its location, and what is
  derived again
- `domain/…/backup/ImportedPlaceCell.kt` — which archived cells are cells at all, where each one is,
  and what an unnamed one leaves behind
- `domain/…/usecase/ExportBackup.kt`, `ImportBackup.kt`
- `domain/…/repository/TransactionRunner.kt` — the one-unit-of-work seam the import runs inside;
  `data/…/db/RoomTransactionRunner.kt` is Room's write transaction behind it
- `domain/…/platform/BackupArchive.kt` — the reader/writer seam
- `data/…/backup/BackupRecords.kt`, `WalkRecords.kt` — the serialized shape and its mappers
- `data/…/androidMain/backup/ZipBackupArchive.android.kt` — the ZIP itself

## At the edges, on screen

- **Export and import share one work name**, so they cannot run at once. An export reads cats, cells
  and walks one query at a time, so an import landing between two of those reads would give an
  archive whose rows come from different sides of the merge.
- **Neither worker retries.** The file picker's grant dies with the process, so a retry would write
  nothing and report success for an archive that does not exist.
- **Both buttons are unavailable while a run is in progress**, and a finished run replaces the
  progress bar with what happened — including *which* thing happened, read back from the worker's
  own tag so an outcome never says "exported" about an import.
- **The suggested filename carries the date**, which is what stops a second export silently offering
  to overwrite the first.

## Not built yet

Per-row progress. A backup of a personal cat counter is small enough that the bar is indeterminate;
if archives ever grow enough to need a count, the worker already has the shape for it.
