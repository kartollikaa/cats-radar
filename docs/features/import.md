# Importing photos from the gallery

A cat you photographed before the app existed, or on a walk where you forgot to open it, is still a
cat. Import turns picked photos into encounters that sit in the history at the time they were taken,
not at the time you imported them.

**Nothing reaches this yet.** The rules and the use case exist; the picker, the worker and the
summary are the next slice, so today no tap can start an import.

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
  `locationFixedAt` set to when the photo was taken rather than when it was imported.
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

- **A Photo Picker URI serves a narrow projection** and throws on columns it does not recognise, so
  each date column is asked for on its own and a refusal reads as "no date" rather than a failed
  import. `DATE_TAKEN` is milliseconds; `DATE_ADDED` and `DATE_MODIFIED` are seconds.
- **Half a coordinate pair is no coordinate pair.** A latitude without a longitude is treated as no
  EXIF location at all.

## Where the code lives

- `domain/…/photo/ImportRules.kt` — the two decisions, as pure functions
- `domain/…/usecase/ImportPhotos.kt` — the run, and what it reports
- `domain/…/platform/SourceFileTime.kt`, `data/…/androidMain/platform/MediaStoreSourceFileTime.android.kt`

## Not built yet

The entry point, the worker with its progress notification, the summary and its undo, and the
location fix for photos that asked for one. Whether EXIF GPS survives scoped storage on a Photo
Picker URI is unverified — the design spec flags it as an open item, and the answer decides whether
the EXIF branch above is ever taken in practice.
