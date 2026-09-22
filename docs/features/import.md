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

- Coordinates in the photo's own EXIF → they become the encounter's, geohashed, with
  `locationFixedAt` set to when the photo was taken rather than when it was imported. The place cell
  they fall in is created at the same moment, so the photo can be named like any other located cat —
  no worker runs for these, and nothing else would create it. **In practice this branch is never
  taken from the photo picker** — see below — but it is what an unredacted source would get.
- No coordinates, and taken within `RECENT_PHOTO_WINDOW` of now → the photo was probably just taken
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
- **Half a coordinate pair is no coordinate pair.** A latitude without a longitude is treated as no
  EXIF location at all.

## Where the code lives

- `domain/…/photo/ImportRules.kt` — the two decisions, as pure functions
- `domain/…/usecase/ImportPhotos.kt` — the run, and what it reports
- `domain/…/region/PlaceCells.kt` — shared with `AttachLocation`: coordinates always get a cell
- `domain/…/platform/SourceFileTime.kt`, `data/…/androidMain/platform/MediaStoreSourceFileTime.android.kt`

## Not built yet

**No progress notification.** Expedited work needs none to run, so an import that finishes while the
app is open is fully covered; an import the user walks away from currently reports only when they
come back. The notification — channel, `POST_NOTIFICATIONS`, a foreground service type — is its own
slice.

**No Settings entry point** — the long-press is the only way in today.

**`ACCESS_MEDIA_LOCATION` is not requested.** It plus `MediaStore.setRequireOriginal` is the
documented way to ask for unredacted EXIF, and it would cost the user another permission dialog for
a benefit we have no evidence the picker will grant. Worth revisiting only if location on imported
photos turns out to matter.
