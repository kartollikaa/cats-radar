# Importing photos from the gallery

A cat you photographed before the app existed, or on a walk where you forgot to open it, is still a
cat. Import turns picked photos into encounters that sit in the history at the time they were taken,
not at the time you imported them.

**Long-press the camera button** on the Counter to pick photos. The run happens in a worker, so it
survives leaving the screen; the Counter shows how far it has got, and at the end what was added,
skipped and failed, with one undo for the whole batch.

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
  like any other located cat — no worker runs for these, and nothing else would create it. **In
  practice this branch is never taken from the photo picker** — see below — but it is what an
  unredacted source would get.
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

- **The photo picker hands over a redacted copy, not the file on disk.** Verified on a device: the
  bytes received hash differently from the original, and the GPS tags are gone, so an imported photo
  gets no location however carefully the original recorded one. The **date survives** — an imported
  photo lands on the day it was taken, which is what matters most here.
- Because the bytes are re-encoded, `sourceDigest` is the digest of the *redacted* copy. That is
  fine for dedup because the redaction is deterministic: picking the same photo twice produces the
  same digest and the second one is skipped. It does mean a digest never matches the same photo
  imported through some other path.
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

**No Settings entry point** — the long-press is the only way in today.

**`ACCESS_MEDIA_LOCATION` is not requested.** It plus `MediaStore.setRequireOriginal` is the
documented way to ask for unredacted EXIF, and it would cost the user another permission dialog for
a benefit we have no evidence the picker will grant. Worth revisiting only if location on imported
photos turns out to matter.
