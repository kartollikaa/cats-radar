# Importing photos from the gallery

A cat you photographed before the app existed, or on a walk where you forgot to open it, is still a
cat. Import turns picked photos into encounters that sit in the history at the time they were taken,
not at the time you imported them.

The Counter's Photo button is a split button: its main part opens the camera, and **the gallery icon
at its end** opens the system photo picker; holding that icon names it. The run happens in a worker,
so it survives leaving the screen; the Counter shows how far it has got, and at the end, briefly,
what was added, skipped and failed, with one undo for the whole batch.

## What an imported photo becomes

An encounter with `kind = PHOTO` and `origin = GALLERY`. The app writes its own compressed copy and
thumbnail exactly as the camera path does, and leaves the original alone: it is already in the
gallery, so `galleryUri` stays null. Copying it back would give the user two of the same picture.

## When it happened

In order, first answer wins:

1. EXIF `DateTimeOriginal`. If the camera also wrote `OffsetTimeOriginal`, that offset is stored;
   otherwise the device's zone **at that date** supplies it, so a photo from the other side of a DST
   change does not land an hour out.
2. The file's own date, as the provider behind the URI reports it.
3. Now.

A recorded offset never travels to a fallback date. The EXIF reader parses the offset and the
timestamp independently, so a photo can carry an offset and no time at all — and an offset that
describes a timestamp we do not have says nothing about the file's date.

## Where it happened

- Coordinates in the photo's own EXIF that are a point on the globe → they become the encounter's,
  geohashed, with `locationFixedAt` set to when the photo was taken rather than when it was
  imported. The place cell they fall in is created at the same moment, so the photo can be named
  like any other located cat — no worker runs for these, and nothing else would create it. The
  picker hands the coordinates over only when the user agrees to share them — see below.
- No usable coordinates, and taken within `RECENT_PHOTO_WINDOW` of now → the photo was probably just taken
  where the phone is standing, so it is worth asking for a fix.
- Otherwise → no location, ever. **A historical photo never receives today's location**; that is the
  one rule the whole design of this feature exists to protect.

The window applies in both directions. A photo dated a few minutes ahead of now is a clock running
fast and still counts as taken here; one dated years ahead is as meaningless as one years old.

## What a run reports

Per photo: added, skipped, or failed.

- **Skipped** — an encounter already holds a photo with the same SHA-256. The digest is computed
  before anything is written, so importing the same picture twice costs no disk at all. Two picks of
  the same photo inside one batch collapse the same way.
- **Failed** — the image could not be decoded, so there is no copy to point an encounter at. The
  rest of the batch continues; one unreadable file does not abandon the other ninety-nine.

A soft-deleted twin does **not** block a re-import. Deleting a cat and picking its photo again is a
deliberate act, and refusing it would leave the user unable to undo their own deletion.

**Undo takes the whole run back in one write**: `UndoImport` hands every cat of the run to
`softDeleteAll` with one `deletedAt` (`UndoImportTest`), and `EncounterDao.softDeleteAll` runs it as
one Room transaction, so the run goes all or none and the list and statistics redraw once rather
than once per cat. A write that fails leaves every cat in place and keeps Undo on offer, so the
user can simply try again (*a failed undo of an import keeps the cats and the undo, and a retry
takes them back*).

## At the edges

- **A summary goes away on its own, and never comes back after.** It stays for
  `IMPORT_SUMMARY_VISIBLE` from the moment the Counter learns the run has finished, and running out
  does what OK does: the Undo lapses with it. A successful Undo, OK, or the time running out records
  that run as dealt with (`SettingsRepository.acknowledgedRun`, kept in DataStore next to the
  settings). After that, reading the same run back shows nothing and offers no second Undo. A failed
  Undo records nothing, so the offer survives it until the time runs out; one that fails after the
  time has run out puts nothing back.
- **The countdown does not pause in the background.** A run that finishes while the user is in
  another app can have timed out by the time they return. WorkManager keeps a finished run, and the
  Counter reads it back each time it is shown and again after a restart, so a summary whose time
  never ran out — the app was closed or killed first — comes back with a fresh countdown.
- **The photo picker strips GPS unless the user shares it.** The import asks for location with
  `MediaStore.EXTRA_REQUEST_LOCATION_METADATA_ACCESS`. The picker then asks *Include location info?*
  once, remembers the answer for this app, and keeps a location button in its corner to change it.
  Declined, the bytes handed over are a redacted copy: the GPS tags are gone and the photo gets no
  location. A picker that predates the request ignores it and behaves as it always did; the system
  picker learns the request from its own Google Play system updates, not from an Android release.
  The **date survives** either way, so an imported photo still lands on the day it was taken. No
  runtime permission is involved: the app neither requests `ACCESS_MEDIA_LOCATION` nor opens the
  photo through `MediaStore.setRequireOriginal`.
- `sourceDigest` is the digest of the bytes the picker handed over, and a redacted copy hashes
  differently from the original. The redaction is deterministic, so picking the same photo twice
  with the same location choice produces the same digest and the second one is skipped. Picked once
  without location and once with it, the same photo has two digests and imports twice: importing a
  cat again does not add a location to the copy already there. A digest also never matches the same
  photo imported through some other path.
- **A Photo Picker URI serves a narrow projection** and throws on columns it does not recognise, so
  each date column is asked for on its own and a refusal reads as "no date" rather than a failed
  import. `DATE_TAKEN` is milliseconds; `DATE_ADDED` and `DATE_MODIFIED` are seconds.
- **Half a coordinate pair, or a pair that is not a point on the globe, is no coordinate pair.** A
  latitude without a longitude, a latitude beyond ±90, a longitude beyond ±180 or a value that is
  not a number is treated as no EXIF location at all — so a recent photo still asks for a fix, and
  an older one gets no location rather than an impossible one.

## Where the code lives

- `domain/…/photo/ImportRules.kt` — the two decisions, as pure functions
- `domain/…/usecase/ImportPhotos.kt` — the run, and what it reports
- `domain/…/region/PlaceCells.kt` — shared with `AttachLocation`: coordinates always get a cell
- `domain/…/platform/SourceFileTime.kt`, `data/…/androidMain/platform/MediaStoreSourceFileTime.android.kt`
- `presentation/…/ReportedRun.kt` — which finished run the summary reports, shared with Backup
- `app/…/photo/PickPhotosWithLocation.kt` — the picker, asked for each photo's location

## Walking away mid-import

The worker posts one ongoing notification and updates it in place as photos land, so an import the
user leaves is still legible. It is cleared in a `finally`: an ongoing notification left behind is
one the user cannot swipe away.

Posting is best-effort. `POST_NOTIFICATIONS` is asked for **after** the pick, not before — a run the
user has actually started is the only moment a progress bar is worth a dialog — and a refusal still
imports, just without the commentary. `NotificationManagerCompat.notify` raises without the
permission rather than doing nothing, so the check guards the call itself.

There is no foreground service. Expedited work does not need one to run, and a service type would
buy a notification that the OS, rather than the app, keeps alive — for a job that takes seconds.

## Not built yet

**No Settings entry point** — the gallery half of the Photo button is the only way in.

**No repair for cats imported without a location.** A cat that arrived redacted keeps no location;
picking its photo again with location shared adds a second cat rather than filling in the first.
