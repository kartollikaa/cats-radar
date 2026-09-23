# Photos

Taking a photo of a cat logs the cat. The pieces behind it: reading a photo's own metadata, making
the app's copies, hashing the original, and putting it in the device gallery.

Interfaces live in `:domain` and know nothing about Android; the implementations are in
`:data/androidMain`. Every one of them degrades rather than throwing — a photo is worth saving even
when something about it cannot be read.

## Taking one

The Photo button on the counter opens the system camera, which writes its original to a
`FileProvider` URI under the cache directory. On the way back `LogPhoto` reads the EXIF, stores the
app's copies, hands the original to the gallery if the setting allows, and saves one encounter with
`kind = PHOTO`, `origin = CAMERA` and the original's digest. The widget's Photo tile ends up on the
same path: it opens the app on the counter and presses that button (see [widget.md](./widget.md)).

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

## The gallery setting

`saveOriginalsToGallery` lives in DataStore and is **on unless turned off**, so a fresh install keeps
the user's photos where they expect them. The **Settings** tab has the switch. DataStore stays
inside `:data` behind `createSettingsRepository` — `:app` never names the type, which also keeps the
dependency off `:app`'s classpath.

The switch renders what is *stored*, not what was last tapped: it follows the settings flow rather
than keeping its own optimistic state, so a failed write cannot leave the two disagreeing.

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

## Where the code lives

- `domain/…/photo/ScaledSize.kt` — `scaleToFit`
- `domain/…/platform/ExifReader.kt`, `PhotoPlatform.kt` — the interfaces
- `data/…/androidMain/platform/` — `AndroidExifReader`, `AndroidImageResizer`, `Sha256Digest`,
  `MediaStoreGallerySaver`, `AndroidPhotoStorage`
- `tools/make-photo-fixtures.py`, `data/src/androidHostTest/resources/photos/`

## Seeing one

A photo encounter shows its thumbnail in the Encounters list and the app's full copy on the detail
screen, both loaded from app-private storage with Coil. The mapper resolves the stored **relative**
path into an absolute one — the row carries a path Coil can open, not the path the database happens
to hold.

A row with no path renders a placeholder of the same size, covering both a tally, which never had a
photo, and a photo whose thumbnail failed to write while the copy succeeded. The list keeps its
rhythm either way rather than shifting when a thumbnail is missing.

## Not built yet

The gallery-import rules exist but nothing can reach them yet — see `import.md`.
`PhotoStorage` is named that, not `PhotoStore` as the design spec had it, because the
`*Store` suffix belongs to MVI stores in `:presentation` and a Konsist test enforces it.
