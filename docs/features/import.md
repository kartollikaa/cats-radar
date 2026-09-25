# Importing photos from the gallery

A cat you photographed before the app existed, or on a walk where you forgot to open it, is still a
cat. Import turns picked photos into encounters that sit in the history at the time they were taken,
not at the time you imported them.

The Counter's Photo button is a split button: its main part opens the camera, and **the gallery icon
at its end** asks for access to where photos were taken, then opens the gallery; holding that icon
names it. The run happens in a worker, so it survives leaving the screen; the Counter shows how far
it has got, and at the end, briefly, what was added, skipped and failed, with one undo for the
whole batch.

Both show as a card above the count, on the same low surface as the Statistics and Settings rows,
with a round icon at its start. While the run goes, the card shows the gallery icon, **Importing
photos**, "7 of 23" and a progress bar. When it has finished, the card shows a check, the number
added and, only when there were any, the skipped and failed lines, with **Undo** at its end, or
**OK** when there is nothing to undo: the run added no cat, or has been undone.

## What an imported photo becomes

An encounter with `kind = PHOTO` and `origin = GALLERY`. The app writes its own compressed copy and
thumbnail exactly as the camera path does, and leaves the original alone: it is already in the
gallery, so `galleryUri` stays null. Copying it back would give the user two of the same picture
(`ImportPhotosTest`, *an import from the phone's gallery remembers the item it came from, and is still
not copied back*).

## The gallery item it came from

The imported cat also keeps the gallery item the photo was picked as, in `sourceMediaUri`, so the
photo viewer can open the original where the user keeps it (see
[photo-viewer.md](./photo-viewer.md#open-in-gallery)). Which item that is comes from the URI the
gallery handed over, not from reading the gallery, which the app may have no permission to do:

- **The system photo picker** names the item as `content://media/picker…/<user>/<provider>/media/<id>`.
  When the provider is the phone's own, `<id>` is the item's MediaStore id, so the cat keeps
  `content://media/external/images/media/<id>` (`MediaStoreItemLocatorTest`,
  *aPhotoImportedThroughGetContentIsTheMediaStoreItemItNames*). That shape is not public API; it is
  pinned by tests on URIs captured from a device.
- **A photo only in the cloud** comes from the cloud provider, whose `<id>` is its own, and **a photo
  from another profile** (a work profile) names another user — neither has an item here, so the cat
  keeps none (*aCloudOnlyPhotoHasNoItemOnThisDevice*, *aPhotoFromAnotherProfileHasNoItemForThisOne*).
- **The Files app's image views** hand over a media documents URI, which Android's own
  `MediaStore.getMediaUri` converts; when it cannot, no item (*aFilesAppImageIsTheItemAndroidsOwnConversionNames*,
  *aFilesAppImageAndroidCannotConvertHasNoItem*). A file picked by browsing a storage folder comes from
  another provider and has none (*aFilesAppImageFromAStorageFolderHasNoItem*).
- **A gallery app that answers with the MediaStore item itself** keeps that item, without any query
  it came with (*aMediaStoreItemHandedOverAsItselfIsKeptWithoutItsQuery*); any other app's provider,
  or a file, has no item (*anotherAppsProviderHasNoItem*).

A pick with no item is imported all the same, just without the link (`ImportPhotosTest`, *a pick that
names no item on the phone is imported with no link to one*). Cats imported before the app kept the
item have none either.

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
  coordinates reach the app only when it may see where photos were taken — see below.
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
- **A photo keeps its GPS only when the app may see where photos were taken.** Android strips the
  GPS tags from the bytes it hands over unless the app holds `ACCESS_MEDIA_LOCATION`, so the gallery
  icon asks for it first — the import starts reading photos the moment they are picked — and opens
  the gallery whatever the answer. The **date survives** either way, so a photo imported without
  that access still lands on the day it was taken; it just gets no location. Android stops asking
  after a second refusal; the switch then lives in the app's system permissions under *Photos and
  videos*.
- **The dialog reads as access to photos and videos** — to photos, media and files before Android
  13 — because Android files this permission under that group. The app declares no permission to
  read the gallery, so *Allow all* grants it the location of the photos it is handed and nothing
  else. *Allow limited access* first opens a picker of its own for photos to share; the location
  access that comes with it is one-time, lapses once the app has been in the background a while,
  and is asked for again at the next import.
- **The gallery opens with `ACTION_GET_CONTENT`, not the photo picker's own `ACTION_PICK_IMAGES`.**
  MediaProvider strips GPS from a `PICK_IMAGES` photo whatever the app holds. The one way round it
  there, `MediaStore.EXTRA_REQUEST_LOCATION_METADATA_ACCESS`, works only once the picker's own
  update has switched that feature on, and the app cannot tell whether it has. A `GET_CONTENT` photo
  keeps its GPS for an app holding the permission, and `GET_CONTENT` for images is still served by
  the system photo picker wherever the phone has one.
- **Every photo is read as its original where MediaStore allows it.** A gallery app may answer
  `GET_CONTENT` with a plain `content://media/…` URI, whose GPS MediaStore hands over only through
  `MediaStore.setRequireOriginal`. With the permission held, the digest, the EXIF read and the copy
  all ask for the original first, and take the bytes as handed over when the provider refuses.
- **The picked photos stay readable for the whole run.** A read grant handed back by
  `GET_CONTENT` belongs to the activity that received it, so finishing the app mid-import would
  otherwise cut the worker off from every photo it had not read yet. Where the source offers it, the
  grant is persisted on pick and released once the run finishes, successfully or not. A run the
  system stops keeps it for WorkManager's next attempt, and a new pick lets go of whatever an
  earlier batch still holds.
- **A pick keeps its first `IMPORT_BATCH_MAX` photos.** `GET_CONTENT` has no limit of its own; the
  photos past the batch are left out of the run. The cap also bounds the run's summary, whose added
  ids come back through WorkManager's output data, which has a fixed size cap of its own.
- **The picked photos reach the worker in a file, not in WorkManager's input data.** Input data has
  the same fixed size cap, and enqueueing past it throws. A full batch of long URIs outgrows it:
  Google Photos' own, or the photo picker's cloud items. The list is stored under
  `noBackupFilesDir`, keyed by the run's work id, and removed once the run finishes, successfully or
  not. A run the system stops keeps it for WorkManager's next attempt, and a new pick removes
  whatever an earlier run left. A batch that cannot be written (a full disk) or read back counts as
  empty: its run imports nothing and ends, so the progress row still clears.
- `sourceDigest` is the digest of the bytes the app was handed, and a redacted copy hashes
  differently from the original. The redaction is deterministic, so picking the same photo twice
  with the same access produces the same digest and the second one is skipped. Picked once without
  location access and once with it, the same photo has two digests and imports twice: importing a
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
- `app/…/navigation/PhotoLaunchers.kt` (`rememberGalleryImportPicker`) — the permission, then the gallery
- `app/…/photo/PickGalleryPhotos.kt` — the gallery, opened so that photos can keep their GPS
- `app/…/photo/PhotoReadAccess.kt` — the picked photos' read grants, held for the run
- `app/…/worker/ImportBatches.kt` — the picked photos' URIs, stored for the run under its work id
- `data/…/androidMain/platform/PhotoStream.android.kt` — how every photo is opened, original first
- `domain/…/platform/GalleryItemLocator.kt`, `data/…/androidMain/platform/MediaStoreItemLocator.android.kt`
  — which gallery item a picked photo is

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
picking its photo again with location access granted adds a second cat rather than filling in the
first.
