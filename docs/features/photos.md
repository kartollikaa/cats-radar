# Photos

Taking a photo of a cat logs the cat. The pieces behind it: reading a photo's own metadata, making
the app's copies, hashing the original, and putting it in the device gallery.

Interfaces live in `:domain` and know nothing about Android; the implementations are in
`:data/androidMain`. Every one of them degrades rather than throwing — a photo is worth saving even
when something about it cannot be read.

## Taking one

The Photo button on the counter opens the system camera (the gallery icon at its end imports
instead — see [import.md](./import.md)). The camera writes its original to a
`FileProvider` URI under the cache directory. On the way back `LogPhoto` reads the EXIF, stores the
app's copies, hands the original to the gallery if the setting allows, and saves one encounter with
`kind = PHOTO`, `origin = CAMERA` and the original's digest. Once that cat is saved, the counter
asks for its coat (see [coat.md](./coat.md#asked-after-a-photo)). The widget's Photo tile and the
walking notification's Photo button end up on the same path: each opens the app on the counter and
does what a tap on the camera half does (see [widget.md](./widget.md) and
[walking-mode.md](./walking-mode.md#photo)).

The order matters and is deliberate: **the app's own copy is written first**. A gallery item for an
encounter that does not exist would be worse than no gallery item, so an unreadable photo produces
neither (`LogPhotoTest`, *an unreadable photo is never copied to the gallery either*).

A photo that already carries EXIF coordinates keeps them, with `locationSource = EXIF`, and asks for
no fix — the photo knows better than the phone does a minute later. The place cell they fall in is
created in the same breath, because the background attach only runs for a photo that asked for a fix:
nothing else would ever create it, and the cat would read as "no location" in Places despite knowing
exactly where it was. One without coordinates goes to the same background attach a tally uses.

Once `LogPhoto` returns, the original is discarded whatever the outcome. Without that the cache
would grow by one full-size photo per cat; it was found on a device rather than in a test, and now
has both.

### At the edges

- **Cancelled camera** — no encounter, and the file the camera was given is deleted.
- **A camera whose answer never comes** — the app swiped away with the camera still open — leaves its
  file behind until a later start of the app clears it. Only captures older than any camera session
  lasts are cleared, so one that may still be answered, even by the app open in another task, stays.
- **Undecodable photo** — no encounter, one "Photo not saved" message, and the original still goes.
- **Gallery refuses** — the encounter is saved anyway with no `galleryUri`.
- **EXIF coordinates that are not a point on the globe are no coordinates** — a latitude beyond
  ±90, a longitude beyond ±180, a value that is not a number, or only one of the pair. The photo is
  logged without them and goes to the background attach like one with no GPS at all.
  `ExifInterface` applies no range check of its own: a corrupt GPS tag reaches the app as, say,
  200°, and a zero denominator as infinity or, for `0/0`, not a number.

## Giving a cat a photo later

A tally — logged with no photo, from any origin — can be given one afterwards from its detail screen
(see [encounter-detail.md](./encounter-detail.md)): the camera, or a single image picked from the
gallery. `AttachPhoto` stores the app's copy and thumbnail the same way `LogPhoto` does, but under a
name fresh to this attempt rather than the encounter's id, so an attempt that finds the cat already
photographed removes only its own files, never the ones the cat now points at (`AttachPhotoTest`,
*the files are named afresh for the attempt, never after the cat*).

From the camera the original goes to the gallery under the same setting as a photo taken from the
counter (see *The gallery setting* below); from the gallery it is never copied back in
(`AttachPhotoTest`, *a photo from the gallery is never copied back into it*). A photo chosen from the
gallery keeps the item it was picked as instead, by the same rule as an import (see
[import.md](./import.md#the-gallery-item-it-came-from); `AttachPhotoTest`, *a photo chosen from the
gallery remembers the item it came from*); a camera photo never does (*a photo from the camera is never
linked to a picked item*). A cat another install logged keeps neither link — its camera original still
goes to the gallery, the cat just does not point at it — because gallery ids are only this phone's (see
[photo-viewer.md](./photo-viewer.md#open-in-gallery); `AttachPhotoTest`, *a cat another install logged
keeps no link to the original, which still goes to the gallery*).

The write touches only `photoPath`, `thumbPath`, `galleryUri`, `sourceMediaUri`, `sourceDigest` and
`updatedAt`. The cat
keeps the time and place it was logged at, its coat, and its `kind` and `origin` — a tally given a
photo this way is still a tally (`AttachPhotoTest`, *a cat without a photo gets the copy, thumbnail
and digest, and keeps everything else*), and it now counts as "With photo" in Statistics like any
other (see `statistics.md`).

The same photo can go on more than one cat — one picture of two cats together (`AttachPhotoTest`,
*the same photo can go on two cats*). Each cat stores the photo's digest, so importing that picked
photo later is skipped while either cat is live (see `import.md`).

### At the edges

- **An undecodable image, or a write that fails,** leaves the cat unchanged and the screen says
  "Photo not attached"; a failed write's copies are removed (`AttachPhotoTest`, *an undecodable
  photo leaves the cat as it was and nothing in the gallery*; *a write that fails removes the files
  it had written*; `EncounterDetailStoreTest`, *an unreadable photo says so and the offer comes
  back*; *a failed write says the photo was not attached*).
- **The cat is deleted, or already given a photo some other way, while the attempt is running** — it
  is left exactly as it was and the attempt's own copies are removed (`AttachPhotoTest`, *a cat
  deleted while its photo was being copied keeps no files from the attempt*;
  `EncounterDaoAttachPhotoTest`, *attachPhotoNeverReplacesAPhotoTheRowAlreadyHas*). A camera
  original already handed to the gallery stays there: it is the user's photo either way.
- **Leaving mid-attempt** — once the attempt's copies are written, a cancellation before the write
  lands removes them. Once the write has landed, the files are the cat's and stay
  (`AttachPhotoTest`, *a cancellation while the original goes to the gallery removes the copies*;
  *a cancellation after the write has landed keeps the files the cat now points at*).

## The gallery setting

`saveOriginalsToGallery` lives in DataStore and is **on unless turned off**, so a fresh install keeps
the user's photos where they expect them. The **Settings** tab has the switch. DataStore stays
inside `:data` behind `createSettingsRepository` — `:app` never names the type, which also keeps the
dependency off `:app`'s classpath.

The switch renders what is *stored*, not what was last tapped: it follows the settings flow rather
than keeping its own optimistic state, so a failed write cannot leave the two disagreeing.

The `galleryUri` a saved original leaves on its cat is also what the photo viewer's *Open in gallery*
opens, on the installation that saved it and while the gallery still holds the item, ahead of any
picked item the cat also keeps (see [photo-viewer.md](./photo-viewer.md#open-in-gallery)).

## Reading a photo's metadata

`ExifReader` returns latitude, longitude, the moment the shutter fired, and the UTC offset the
camera recorded, each independently absent when the file does not carry it.

Two things there are easy to get wrong and are pinned by tests:

- **No GPS means no coordinates, not `0, 0`.** Null Island is a real point in the Gulf of Guinea; a
  photo that silently claimed it would put a cat in the sea.
- **The instant is derived through a time zone, not through today's offset.** EXIF stores local
  wall-clock time with no zone. When the camera also wrote `OffsetTimeOriginal`, that offset anchors
  it. When it did not, the device's zone does — and converting through the *zone* rather than
  through the offset in force right now is what keeps a photo taken on the other side of a daylight
  saving change from landing an hour out.

A file whose pixels are damaged still yields its metadata, because EXIF sits in the header ahead of
the image data. So a photo that cannot be displayed can still carry a usable location, and nothing
in this layer treats "undecodable" as "unknown location".

## The app's own copies

`ImageResizer` writes two JPEGs into app-private storage: a copy capped at `Tuning.PHOTO_MAX_SIDE`
on its longest side and a thumbnail capped at `Tuning.THUMB_SIZE`. A photo already inside the cap is
left alone — enlarging costs bytes and quality and adds no detail.

Neither copy carries the original's metadata. The app republishes nobody's GPS.

The original is never decoded larger than it needs to be. A phone camera's photo can run to
hundreds of megapixels, and holding one whole in memory fails on a phone; a decode that fails reads
as an unreadable photo, and the cat goes unsaved. The resizer reads the file's
dimensions first and has `BitmapFactory` shrink the decode by the largest power of two that still
leaves the longest side at or above the copy's cap, so the bitmap in memory is never more than
twice the cap on a side, whatever the camera.

- **Powers of two** because the JPEG decoder shrinks by those while decoding, averaging the pixels
  it drops. `BitmapFactory` accepts any other factor too, but meets it by skipping pixels, which
  turns fine detail such as fur into false patterns.
- **The step is chosen rounding down.** JPEG rounds a shrunk side up while other formats may round
  it down, and only rounding down keeps every format at or above the cap, so a copy is never
  enlarged.
- **Both copies are sized from the file's own dimensions,** not from the shrunk bitmap. The decoder
  rounds a halved odd side, and sizing from its result would put a copy a pixel off the original's
  proportions.

Both copies are stored the way the photo is meant to be seen. A phone camera usually saves the
sensor's pixels as they came off it plus an EXIF Orientation tag saying how to turn them — for a
phone held upright, a quarter turn. `BitmapFactory` ignores that tag, and the copies have no EXIF to
pass it on, so the resizer applies the turn, or the mirroring, to the pixels itself. Without it a
portrait photo lies on its side in the app while the gallery, which keeps the original, shows it
upright. `ImageDecoder` would shrink to an exact size in one call, but it applies the tag on its own,
so on top of the resizer's turn it would turn every rotated photo twice.

The arithmetic — which side is longest, what the other becomes, when to do nothing — is
`scaleToFit` in `:domain`, a pure function with its own tests. That split is deliberate: see
*Testing the pixels* below.

## Hashing and the gallery

`Digest` is SHA-256 over the original bytes, used later to skip a photo that has already been
imported. `GallerySaver` copies the original into `Pictures/Cats Radar` through MediaStore.

The gallery is the one place where failure is routine — a full volume, a missing external volume, a
revoked permission — so `save` returns no URI instead of throwing, and the encounter is saved
either way. It writes the item as *pending* and clears the flag only once the bytes are there; if
anything fails in between it deletes the row, because a pending item nobody finishes writing sits in
the user's gallery invisible and undeletable.

## At the edges

- **A path that climbs out of the photo directory is refused.** Stored paths are data, and data can
  be wrong — a `../` in a corrupt or imported row would otherwise reach the database file next door
  (`AndroidPhotoStorageTest`, *a path trying to climb out of the photo directory is refused*).
- **An undecodable source stores nothing** and says so, rather than leaving a half-written file.
- **A photo with no orientation tag is stored as decoded.**
- **A missing thumbnail is not a missing photo.** The copy failing fails the call; the thumbnail
  failing leaves the copy in place with no thumbnail, for the UI to render a placeholder.
- **Deleting a file that is not there is not an error**, so a retry after a partial failure is safe.

## Testing the pixels

Robolectric's default graphics mode is a shadow: `BitmapFactory` hands back a 100×100 stub whatever
the file contains, and `compress` writes a descriptor string rather than JPEG bytes. Every size
assertion would then hold no matter what the resizer did — a whole test class that cannot fail.

`AndroidImageResizerTest` therefore runs under `@GraphicsMode(NATIVE)`, and its first test is a
positive control: decode a fixture and assert it reports 3000×2250. If that one ever reports
100×100, the shadow is back and none of the others mean anything.

The fixtures are generated by `tools/make-photo-fixtures.py` using Pillow — deliberately a different
implementation from the `androidx.exifinterface` that reads them back, so agreement between the two
is evidence rather than tautology. Expected digests come from `shasum -a 256`, outside Kotlin, for
the same reason. They are gradients rather than flat colours: a flat image survives any resampling
unchanged and could not tell a correct resize from a broken one.

The orientation fixtures are the exception: one portrait picture in four coloured quadrants, saved
once per EXIF orientation with its pixels laid out the way a camera would lay them out for that tag.
Four distinct corners put each of the eight orientations in a different order, which a gradient's
corners after JPEG and resampling tell apart less reliably. The generator checks every one with
Pillow's `ImageOps.exif_transpose` before keeping it, so the answer `AndroidImageResizerOrientationTest` expects
never comes from the code under test.

One of them is phone-sized: the quarter-turn case at more than twice the copy's cap on its longest
side, so its decode has to shrink. `AndroidImageResizerLargePhotoTest` checks the decode itself —
shrunk, but never below the cap — and the sizes of both copies. The fixture's size is picked on
purpose: its long side is odd, so halving rounds it up, and at this size that moves the copy's short
side by a pixel. A copy sized from the shrunk bitmap instead of the file comes out a pixel narrower,
and the size test fails; most sizes, odd or not, would hide that. Flat quadrants compress to almost
nothing, which keeps a fixture that large small in the repository.

## Where the code lives

- `domain/…/photo/ScaledSize.kt` — `scaleToFit`
- `domain/…/geo/Globe.kt` — `pointOnGlobe`, whether a pair of coordinates counts as a location
- `domain/…/platform/ExifReader.kt`, `PhotoPlatform.kt` — the interfaces
- `domain/…/usecase/AttachPhoto.kt` — giving a logged cat a photo; `domain/…/model/EncounterPhoto.kt` — a cat's
  photo (see [data-model.md](./data-model.md#photos))
- `data/…/androidMain/platform/` — `AndroidExifReader`, `AndroidImageResizer`, `Sha256Digest`,
  `MediaStoreGallerySaver`, `AndroidPhotoStorage`
- `tools/make-photo-fixtures.py`, `data/src/androidHostTest/resources/photos/`

## Seeing one

A photo encounter shows its thumbnail in an Encounters tile or card, the app's full copy when it
shares a pair row with the photo next to it (see `browsing-cats.md`), and the full copy on the detail
screen, all loaded from app-private storage with Coil; a tap on the detail screen's photo opens the
same copy fullscreen (see [photo-viewer.md](./photo-viewer.md)). The mapper resolves the stored **relative**
path into an absolute one — the cell carries a path Coil can open, not the path the database happens
to hold. A pair tile with no full copy falls back to its thumbnail.

A cat with no thumbnail leads with its coat, or a paw when no coat was noted, in a tile the same
size — covering both a tally with no photo yet and a photo whose thumbnail failed to write while
the copy succeeded. Such a photo never joins a pair: the grid packs it like any cat without one.

## Not built yet

A cat's photo cannot be replaced or removed — there is no control for either.
`PhotoStorage` is named that, not `PhotoStore` as the design spec had it, because the
`*Store` suffix belongs to MVI stores in `:presentation` and a Konsist test enforces it.
